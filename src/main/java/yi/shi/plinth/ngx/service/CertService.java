package yi.shi.plinth.ngx.service;

import cn.dev33.satoken.stp.StpUtil;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import yi.shi.plinth.ngx.NginxPanelException;
import yi.shi.plinth.ngx.NginxProperties;
import yi.shi.plinth.ngx.dto.CertView;
import yi.shi.plinth.ngx.dto.SaveResult;
import yi.shi.plinth.user.UserService;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 证书管理服务：清单发现（配置 ssl_certificate 指令）、解析（X509）、
 * 替换流水线（密码二次确认 + 预检证书/私钥匹配 + nginx -t 失败还原 + 版本留痕）、
 * 自签证书生成（BouncyCastle）。
 *
 * <p>证书/私钥匹配用「签名挑战自验」：私钥签名随机串、证书公钥验签，
 * RSA 与 ECDSA 通吃，无需按算法反推公钥。
 */
@Slf4j
@Singleton
public class CertService {

    private static final long MAX_SCAN_BYTES = 2 * 1024 * 1024;
    private static final Pattern CERT_DIRECTIVE = Pattern.compile(
            "^[ \\t]*ssl_certificate[ \\t]+([^;\\s]+)", Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);
    private static final Pattern KEY_DIRECTIVE = Pattern.compile(
            "^[ \\t]*ssl_certificate_key[ \\t]+([^;\\s]+)", Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);

    /** 相对路径解析基准：confDir 父目录（与 LogService 统一） */
    private final Path baseDir = NginxProperties.confDirPath().getParent();

    @Inject
    private ConfWriteLock confWriteLock;

    @Inject
    private NginxControlService nginxControlService;

    @Inject
    private VersionService versionService;

    @Inject
    private AuditService auditService;

    @Inject
    private UserService userService;

    // ---------- 清单 ----------

    /** 发现当前配置引用的全部证书（cert + 配对 key + X509 详情），按剩余天数升序。 */
    public List<CertView> list() {
        Map<String, String> pairs = scanCertPairs();
        List<CertView> views = new ArrayList<>();
        for (Map.Entry<String, String> e : pairs.entrySet()) {
            Path certFile = resolve(e.getKey());
            CertView view = new CertView();
            view.setCertPath(certFile.toString());
            view.setKeyPath(e.getValue() == null ? null : resolve(e.getValue()).toString());
            fillCertInfo(view, certFile);
            view.setManaged(certFile.toAbsolutePath().normalize()
                    .startsWith(NginxProperties.certDirPath().toAbsolutePath()));
            views.add(view);
        }
        views.sort((a, b) -> Long.compare(
                a.getDaysLeft() == null ? Long.MAX_VALUE : a.getDaysLeft(),
                b.getDaysLeft() == null ? Long.MAX_VALUE : b.getDaysLeft()));
        return views;
    }

    // ---------- 替换 ----------

    /**
     * 替换证书（可同时换私钥）：密码二次确认 → 预检（PEM 可解析、新证书与新/旧私钥匹配）
     * → 原子写入 → nginx -t（真实加载证书，格式/路径错误暴露）→ 失败还原 → 版本留痕 → 可选重载。
     */
    public SaveResult replace(String certPath, String certPem, String keyPem,
                              String password, String reason, Boolean reloadAfter) {
        requirePassword(password, certPath, "REPLACE_CERT", "证书替换");
        Path certFile = Paths.get(certPath);
        assertCertTarget(certFile);
        List<X509Certificate> certs = parseCerts(certPem);

        SaveResult result = new SaveResult();
        synchronized (confWriteLock.monitor) {
            Path keyFile = keyOf(certFile);
            String oldCert = exists(certFile) ? readString(certFile) : null;
            String oldKey = exists(keyFile) ? readString(keyFile) : null;
            boolean replaceKey = keyPem != null && !keyPem.isBlank();
            try {
                if (replaceKey) {
                    if (!matchCertKey(certs.get(0), parseKey(keyPem))) {
                        throw new NginxPanelException("新证书与新私钥不匹配，已拒绝写入");
                    }
                } else if (exists(keyFile) && !matchCertKeyFile(certs.get(0), keyFile)) {
                    throw new NginxPanelException("新证书与现有私钥不匹配（如需同时更换私钥请一并填写）");
                }

                atomicWrite(certFile, ensureTrailingNewline(certPem));
                if (replaceKey) {
                    atomicWrite(keyFile, ensureTrailingNewline(keyPem));
                }

                var test = nginxControlService.test();
                if (!test.isSuccess()) {
                    restore(certFile, oldCert, keyFile, oldKey);
                    auditService.failed("REPLACE_CERT", certPath,
                            "替换后 nginx -t 失败，已还原\n" + test.getOutput());
                    result.setSuccess(false);
                    result.setMessage("替换已撤销（nginx -t 校验失败，已还原原证书/私钥）");
                    result.setTestOutput(test.getOutput());
                    return result;
                }

                String lsReason = reason == null || reason.isBlank() ? "证书替换" : reason;
                versionService.record(certPathOf(certFile), oldCert, ensureTrailingNewline(certPem), lsReason);
                if (replaceKey) {
                    versionService.record(certPathOf(keyFile), oldKey, ensureTrailingNewline(keyPem), lsReason);
                }
                auditService.success("REPLACE_CERT", certPathOf(certFile),
                        "替换成功：证书 " + certs.size() + " 张，私钥 " + (replaceKey ? "已更换" : "保留原有"));
                result.setSuccess(true);
                result.setMessage("证书替换成功（nginx -t 通过），版本已记录");
                result.setTestOutput(test.getOutput());

                if (Boolean.TRUE.equals(reloadAfter)) {
                    var reload = nginxControlService.reload();
                    result.setReloaded(reload.isSuccess());
                    result.setReloadOutput(reload.getOutput());
                    result.setMessage(result.getMessage() + (reload.isSuccess()
                            ? "，并已重载配置" : "，但重载失败：" + reload.getOutput()));
                    auditService.success("RELOAD", null, reload.isSuccess()
                            ? "证书替换后自动 reload 成功" : "证书替换后 reload 失败\n" + reload.getOutput());
                }
                return result;
            } catch (NginxPanelException e) {
                restore(certFile, oldCert, keyFile, oldKey);
                auditService.failed("REPLACE_CERT", certPathOf(certFile), e.getMessage());
                throw e;
            } catch (IOException e) {
                restore(certFile, oldCert, keyFile, oldKey);
                auditService.failed("REPLACE_CERT", certPathOf(certFile), "写入失败: " + e.getMessage());
                throw new NginxPanelException("证书写入失败: " + e.getMessage(), e);
            }
        }
    }

    // ---------- 自签生成 ----------

    /** 生成自签证书（RSA 2048 / SHA256，SAN 支持），输出 confDir/certs/<name>.crt + .key。 */
    public SaveResult generate(String name, String cn, String sans, Integer days, String password, String reason) {
        if (name == null || !name.matches("[A-Za-z0-9_-]+")) {
            throw new NginxPanelException("文件名只允许字母数字下划线中划线: " + name);
        }
        // 统一收口到面板证书工作目录（cert.dir，默认 /app/certs），不与 /etc/ssl 系统证书混用
        Path certFile = NginxProperties.certDirPath().resolve(name + ".crt");
        Path keyFile = certFile.resolveSibling(name + ".key");
        boolean overwrite = exists(certFile);
        if (overwrite) {
            requirePassword(password, certPathOf(certFile), "GENERATE_CERT", "覆盖同名证书");
        }
        String[] pems;
        try {
            pems = selfSignedPem(cn, sans, days == null ? 3650 : days);
        } catch (NginxPanelException e) {
            throw e;
        } catch (Exception e) {
            throw new NginxPanelException("自签证书生成失败: " + e.getMessage(), e);
        }

        SaveResult result = new SaveResult();
        synchronized (confWriteLock.monitor) {
            try {
                Files.createDirectories(certFile.getParent());
                String lsReason = (reason == null || reason.isBlank() ? "自签证书" : reason)
                        + (overwrite ? "（覆盖）" : "（新建）");
                if (overwrite) {
                    versionService.record(certPathOf(certFile), readString(certFile), pems[0], lsReason);
                    if (exists(keyFile)) {
                        versionService.record(certPathOf(keyFile), readString(keyFile), pems[1], lsReason);
                    }
                }
                atomicWrite(certFile, pems[0]);
                atomicWrite(keyFile, pems[1]);
                auditService.success("GENERATE_CERT", certPathOf(certFile),
                        lsReason + "，CN=" + cn + "，SAN=" + sans);
                result.setSuccess(true);
                result.setMessage("自签证书已生成: " + certFile + " + " + keyFile
                        + "。启用请在站点配置中设置 ssl_certificate 指向它们（保存走校验流水线）。");
                return result;
            } catch (IOException e) {
                throw new NginxPanelException("自签证书写入失败: " + e.getMessage(), e);
            }
        }
    }

    // ---------- 内部实现 ----------

    /** 扫描配置，返回 cert 解析路径 -> 其后紧随的 ssl_certificate_key（可 null）。 */
    private Map<String, String> scanCertPairs() {
        Map<String, String> certToKey = new LinkedHashMap<>();
        try (Stream<Path> stream = Files.walk(NginxProperties.confDirPath(), 3)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> !p.getFileName().toString().startsWith("."))
                    .forEach(p -> {
                        try {
                            if (Files.size(p) > MAX_SCAN_BYTES) {
                                return;
                            }
                            Map<Long, String> certs = new TreeMap<>();
                            Map<Long, String> keys = new TreeMap<>();
                            for (String line : stripped(p.toString())) {
                                Matcher cm = CERT_DIRECTIVE.matcher(line);
                                if (cm.find()) {
                                    certs.put((long) line.indexOf(cm.group(1)), cm.group(1));
                                }
                                Matcher km = KEY_DIRECTIVE.matcher(line);
                                if (km.find()) {
                                    keys.put((long) line.indexOf(km.group(1)), km.group(1));
                                }
                            }
                            if (certs.isEmpty()) {
                                return;
                            }
                            // cert 与其后最近一个 key 配对（nginx 语义：同 server 块内成对出现）
                            List<String> certList = new ArrayList<>(certs.values());
                            List<String> keyList = new ArrayList<>(keys.values());
                            for (int i = 0; i < certList.size(); i++) {
                                String key = i < keyList.size() ? keyList.get(i) : null;
                                certToKey.putIfAbsent(resolve(certList.get(i)).toString(), key);
                            }
                        } catch (IOException e) {
                            log.debug("扫描配置{}失败: {}", p, e.getMessage());
                        }
                    });
        } catch (IOException e) {
            log.warn("扫描配置目录失败: {}", e.getMessage());
        }
        return certToKey;
    }

    /** 读文件并去掉注释行（返回可复用行列表）。 */
    private List<String> stripped(String path) throws IOException {
        List<String> active = new ArrayList<>();
        for (String line : Files.readString(Paths.get(path), StandardCharsets.UTF_8).split("\n", -1)) {
            if (!line.trim().startsWith("#")) {
                active.add(line);
            }
        }
        return active;
    }

    private void fillCertInfo(CertView view, Path certFile) {
        view.setCertExists(false);
        if (!exists(certFile)) {
            view.setDomains(List.of());
            view.setSubject("证书文件不存在");
            view.setDaysLeft(null);
            return;
        }
        try {
            view.setCertExists(true);
            view.setSize(Files.size(certFile));
            view.setLastModified(Files.getLastModifiedTime(certFile).toMillis());
            String pem = readString(certFile);
            List<X509Certificate> certs = parseCerts(pem);
            X509Certificate leaf = certs.get(0);
            view.setDomains(sansOf(leaf));
            view.setSubject(leaf.getSubjectX500Principal().getName());
            view.setIssuer(leaf.getIssuerX500Principal().getName());
            view.setNotAfter(LocalDateTime.ofInstant(leaf.getNotAfter().toInstant(), ZoneId.systemDefault()));
            view.setNotBefore(LocalDateTime.ofInstant(leaf.getNotBefore().toInstant(), ZoneId.systemDefault()));
            long days = Duration.between(LocalDateTime.now(), view.getNotAfter()).toDays();
            view.setDaysLeft(days);
            view.setExpired(days < 0);
            view.setKeyAlg(leaf.getPublicKey().getAlgorithm());
            if (view.getKeyPath() != null && exists(Paths.get(view.getKeyPath()))) {
                view.setKeyMatch(matchCertKeyFile(leaf, Paths.get(view.getKeyPath())) ? Boolean.TRUE : Boolean.FALSE);
            } else {
                view.setKeyMatch(null);
            }
        } catch (Exception e) {
            log.debug("解析证书 {} 失败: {}", certFile, e.getMessage());
            view.setDomains(List.of());
            view.setDaysLeft(null);
            view.setSubject("解析失败: " + e.getMessage());
        }
    }

    private List<X509Certificate> parseCerts(String pem) {
        try {
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            List<X509Certificate> out = new ArrayList<>();
            var collection = factory.generateCertificates(new ByteArrayInputStream(
                    pem.getBytes(StandardCharsets.UTF_8)));
            for (Certificate c : collection) {
                out.add((X509Certificate) c);
            }
            if (out.isEmpty()) {
                throw new NginxPanelException("证书内容中未找到 PEM 结构（BEGIN CERTIFICATE）");
            }
            return out;
        } catch (NginxPanelException e) {
            throw e;
        } catch (Exception e) {
            throw new NginxPanelException("证书解析失败: " + e.getMessage(), e);
        }
    }

    /** 解析私钥 PEM（PKCS8 / RSA / EC，无口令）。 */
    private PrivateKey parseKey(String pemText) {
        try (PEMParser parser = new PEMParser(new StringReader(pemText))) {
            Object obj = parser.readObject();
            if (obj == null) {
                throw new NginxPanelException("私钥内容为空或格式不受支持");
            }
            JcaPEMKeyConverter converter = new JcaPEMKeyConverter();
            if (obj instanceof PEMKeyPair pair) {
                return converter.getKeyPair(pair).getPrivate();
            }
            if (obj instanceof X509CertificateHolder) {
                throw new NginxPanelException("提供的是证书而非私钥");
            }
            if (org.bouncycastle.openssl.PEMEncryptedKeyPair.class.isInstance(obj)
                    || obj instanceof org.bouncycastle.asn1.pkcs.EncryptedPrivateKeyInfo) {
                throw new NginxPanelException("暂不支持带密码保护的私钥，请导出为无口令 PEM");
            }
            if (obj instanceof org.bouncycastle.asn1.pkcs.PrivateKeyInfo info) {
                return converter.getPrivateKey(info);
            }
            throw new NginxPanelException("私钥格式不受支持: " + obj.getClass().getSimpleName());
        } catch (NginxPanelException e) {
            throw e;
        } catch (Exception e) {
            throw new NginxPanelException("私钥解析失败: " + e.getMessage(), e);
        }
    }

    /** 签名挑战自验：私钥签名 + 证书公钥验签。 */
    private boolean matchCertKey(X509Certificate cert, PrivateKey key) {
        try {
            String alg = cert.getPublicKey().getAlgorithm();
            String sigAlg = "EC".equalsIgnoreCase(alg) ? "SHA256withECDSA" : "SHA256withRSA";
            byte[] challenge = "nginx-shell-cert-key-match-v1".getBytes(StandardCharsets.UTF_8);
            Signature s = Signature.getInstance(sigAlg);
            s.initSign(key);
            s.update(challenge);
            byte[] sig = s.sign();
            Signature v = Signature.getInstance(sigAlg);
            v.initVerify(cert.getPublicKey());
            v.update(challenge);
            return v.verify(sig);
        } catch (Exception e) {
            log.debug("证书私钥匹配检查失败: {}", e.getMessage());
            return false;
        }
    }

    private boolean matchCertKeyFile(X509Certificate cert, Path keyFile) {
        try {
            return matchCertKey(cert, parseKey(readString(keyFile)));
        } catch (NginxPanelException e) {
            return false;
        }
    }

    private List<String> sansOf(X509Certificate cert) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        try {
            var sans = cert.getSubjectAlternativeNames();
            if (sans != null) {
                for (List<?> san : sans) {
                    if (Integer.valueOf(2).equals(san.get(0))) {
                        out.add(String.valueOf(san.get(1)));
                    }
                }
            }
        } catch (Exception ignored) {
        }
        if (out.isEmpty()) {
            String cn = cert.getSubjectX500Principal().getName();
            int s = cn.indexOf("CN=");
            if (s >= 0) {
                String v = cn.substring(s + 3);
                int comma = v.indexOf(',');
                out.add(comma > 0 ? v.substring(0, comma) : v);
            }
        }
        return new ArrayList<>(out);
    }

    /** BC：生成 RSA2048 自签 PEM（cert, key）。 */
    private String[] selfSignedPem(String cn, String sans, int days) throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048, new SecureRandom());
        KeyPair pair = kpg.generateKeyPair();

        String cnValue = cn == null || cn.isBlank() ? "nginx-shell.local" : cn.trim();
        X500Name subject = new X500Name("CN=" + cnValue);
        Date notBefore = new Date();
        Date notAfter = new Date(System.currentTimeMillis() + days * 86400000L);
        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                subject, BigInteger.valueOf(System.currentTimeMillis()),
                notBefore, notAfter, subject, pair.getPublic());
        Set<String> sanList = new LinkedHashSet<>();
        sanList.add(cnValue);
        if (sans != null && !sans.isBlank()) {
            for (String s : sans.split(",")) {
                if (!s.isBlank()) {
                    sanList.add(s.trim());
                }
            }
        }
        GeneralName[] names = sanList.stream()
                .map(h -> new GeneralName(GeneralName.dNSName, h))
                .toArray(GeneralName[]::new);
        builder.addExtension(Extension.subjectAlternativeName, false, new org.bouncycastle.asn1.x509.GeneralNames(names));
        var signer = new JcaContentSignerBuilder("SHA256withRSA").build(pair.getPrivate());
        X509CertificateHolder holder = builder.build(signer);
        X509Certificate cert = new JcaX509CertificateConverter().getCertificate(holder);

        StringWriter certW = new StringWriter();
        try (JcaPEMWriter w = new JcaPEMWriter(certW)) {
            w.writeObject(cert);
        }
        StringWriter keyW = new StringWriter();
        try (JcaPEMWriter w = new JcaPEMWriter(keyW)) {
            w.writeObject(pair.getPrivate());
        }
        return new String[]{certW.toString(), keyW.toString()};
    }

    private void requirePassword(String password, String path, String action, String op) {
        if (password == null || password.isBlank()) {
            throw new NginxPanelException(op + "需要输入管理员密码确认");
        }
        if (!userService.verifyPassword(StpUtil.getLoginIdAsLong(), password)) {
            auditService.failed(action, path, "密码确认失败，" + op + "被拒绝");
            throw new NginxPanelException("密码错误，操作已拒绝");
        }
    }

    private void assertCertTarget(Path certFile) {
        String s = certPathOf(certFile).replace('\\', '/');
        if (s.contains("..")) {
            throw new NginxPanelException("非法路径: " + s);
        }
        String lower = s.toLowerCase();
        if (!lower.endsWith(".crt") && !lower.endsWith(".pem") && !lower.endsWith(".cert")) {
            throw new NginxPanelException("仅允许替换 .crt/.pem/.cert 文件: " + s);
        }
        // 目标必须位于面板证书工作目录（cert.dir，默认 /app/certs）：
        // /etc/ssl 等系统证书目录、任意其它路径一律禁止面板覆盖，避免与系统证书混用
        Path certRoot = NginxProperties.certDirPath().toAbsolutePath();
        Path normalized = certFile.toAbsolutePath().normalize();
        if (!normalized.startsWith(certRoot)) {
            throw new NginxPanelException("仅允许替换面板证书目录内的文件（" + certRoot + "）；"
                    + "系统证书（/etc/ssl 等）与其它路径请先修改配置中的 ssl_certificate 指向面板证书目录");
        }
    }

    private void restore(Path certFile, String oldCert, Path keyFile, String oldKey) {
        try {
            if (oldCert != null) {
                atomicWrite(certFile, oldCert);
            } else {
                Files.deleteIfExists(certFile);
            }
        } catch (IOException e) {
            log.error("还原证书文件失败: {}", certFile, e);
        }
        try {
            if (oldKey != null) {
                atomicWrite(keyFile, oldKey);
            } else {
                Files.deleteIfExists(keyFile);
            }
        } catch (IOException e) {
            log.error("还原私钥文件失败: {}", keyFile, e);
        }
    }

    private Path keyOf(Path certFile) {
        String s = certFile.toString();
        int dot = s.lastIndexOf('.');
        String stem = dot > 0 ? s.substring(0, dot) : s;
        return Paths.get(stem + ".key");
    }

    private String certPathOf(Path p) {
        return p.toString();
    }

    /** 相对路径双候选解析：nginx 相对路径按运行 prefix 解析，这里按「confDir 父目录」与「confDir」
     * 两个常见布局都解析，优先已存在的那个（未存在则取 prefix 基准）。 */
    private Path resolve(String path) {
        Path p = Paths.get(path);
        if (p.isAbsolute()) {
            return p;
        }
        Path byPrefix = baseDir != null ? baseDir.resolve(path) : NginxProperties.confDirPath().resolve(path);
        Path byConfDir = NginxProperties.confDirPath().resolve(path);
        return Files.exists(byPrefix) ? byPrefix : (Files.exists(byConfDir) ? byConfDir : byPrefix);
    }

    private boolean exists(Path p) {
        return p != null && Files.exists(p) && Files.isRegularFile(p);
    }

    private String readString(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new NginxPanelException("读取文件失败: " + p, e);
        }
    }

    private String ensureTrailingNewline(String s) {
        return s == null || s.endsWith("\n") ? s : s + "\n";
    }

    private void atomicWrite(Path target, String content) throws IOException {
        Files.createDirectories(target.getParent());
        Path tmp = target.resolveSibling(target.getFileName().toString() + "." + UUID.randomUUID() + ".tmp");
        Files.writeString(tmp, content, StandardCharsets.UTF_8);
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
