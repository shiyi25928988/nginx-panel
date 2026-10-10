package yi.shi.plinth.ngx.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import yi.shi.plinth.ngx.NginxPanelException;
import yi.shi.plinth.ngx.NginxProperties;
import yi.shi.plinth.ngx.dto.LogFile;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * nginx 日志发现与读取。
 *
 * <p>核心设计（应对"用户改了 nginx.conf 日志位置"）：
 * <ol>
 *   <li>实时发现：每次请求扫描配置目录（nginx.conf / conf.d / sites-enabled）里的
 *       {@code error_log} / {@code access_log} 指令，动态收集日志路径；</li>
 *   <li>二进制默认兜底：解析 {@code nginx -V} 的 {@code --error-log-path=}/{@code --http-log-path=}
 *       编译默认值，两路合并；</li>
 *   <li>安全读取：tail 接口仅允许读"发现清单"内的路径（含 .1 轮转变体），
 *       拒绝任意路径；少量行用 RandomAccessFile 从尾部读，不整文件加载。</li>
 * </ol>
 */
@Slf4j
@Singleton
public class LogService {

    /** 单次日志 tail 读取的最大字节数（超出则从中间截断，标记 truncated） */
    private static final long MAX_TAIL_BYTES = 2 * 1024 * 1024;
    private static final int MAX_TAIL_LINES = 5000;
    private static final long MAX_SCAN_BYTES = 2 * 1024 * 1024;

    /** error_log / access_log <path> [level|format][;] —— 不含 syslog:/memory:/stderr */
    private static final Pattern LOG_DIRECTIVE = Pattern.compile(
            "^[ \\t]*(error_log|access_log)[ \\t]+([^;\\s]+)[ \\t]*",
            Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);

    private static final Pattern V_ERROR_LOG = Pattern.compile("--error-log-path=(\\S+)");
    private static final Pattern V_HTTP_LOG = Pattern.compile("--http-log-path=(\\S+)");

    @Inject
    private NginxControlService nginxControlService;

    /** 发现当前生效配置声明的全部日志文件（+二进制默认），按路径去重。 */
    public List<LogFile> discover() {
        // 相对路径元素的解析基准（nginx 语义是 prefix=cwd，这里用 confDir 父目录近似，
        // 与常见部署 conf/../logs 结构吻合；发行版配置里基本用绝对路径）
        Path base = NginxProperties.confDirPath().getParent();

        // 来源一：二进制编译默认值（nginx -V 输出含 --error-log-path / --http-log-path）
        Set<String> defaults = new LinkedHashSet<>();
        String fullVersionScript = nginxControlService.fullVersion();
        Matcher m1 = V_ERROR_LOG.matcher(fullVersionScript);
        if (m1.find()) {
            defaults.add(m1.group(1));
        }
        Matcher m2 = V_HTTP_LOG.matcher(fullVersionScript);
        if (m2.find()) {
            defaults.add(m2.group(1));
        }

        // 来源二：配置指令声明（优先级高于二进制默认）
        Set<String> fromConfig = new LinkedHashSet<>();
        scanConfDir(fromConfig);

        Map<String, String> byPath = new LinkedHashMap<>();
        defaults.forEach(p -> byPath.put(p, "default"));
        fromConfig.forEach(p -> byPath.put(p, "config"));
        return buildList(byPath, base);
    }

    /** 拉取某日志尾部 N 行。path 必须在发现清单内（防任意文件读取）。 */
    public String tail(String path, int lines) throws IOException {
        if (lines <= 0) {
            lines = 200;
        }
        if (lines > MAX_TAIL_LINES) {
            lines = MAX_TAIL_LINES;
        }
        path = normalizeReq(path);
        if (!isAllowedPath(path)) {
            throw new NginxPanelException("路径不在 nginx 日志发现清单内: " + path);
        }
        if (NginxProperties.isDockerMode()) {
            // docker 模式日志在容器内：docker exec <容器> tail -n N <path>（数组参数，不过 shell）
            var result = nginxControlService.dockerTail(path, lines);
            if (!result.isSuccess()) {
                throw new NginxPanelException("容器内读取日志失败: " + result.getOutput());
            }
            return result.getOutput();
        }
        return tailNative(Paths.get(path), lines, new boolean[1]);
    }

    // ---------- 内部 ----------

    /** 扫描配置目录里全部文本配置文件中的 log 指令。 */
    private void scanConfDir(Set<String> into) {
        Path root = NginxProperties.confDirPath();
        try (Stream<Path> stream = Files.walk(root, 3)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> !p.getFileName().toString().startsWith("."))
                    .forEach(p -> {
                        try {
                            if (Files.size(p) > MAX_SCAN_BYTES) {
                                return;
                            }
                            String text = Files.readString(p, StandardCharsets.UTF_8);
                            // 去掉注释行，避免被注释掉的指令干扰
                            StringBuilder active = new StringBuilder();
                            for (String line : text.split("\n", -1)) {
                                if (!line.trim().startsWith("#")) {
                                    active.append(line).append('\n');
                                }
                            }
                            Matcher m = LOG_DIRECTIVE.matcher(active);
                            while (m.find()) {
                                String p2 = m.group(2);
                                if (!(p2.startsWith("syslog:") || p2.startsWith("memory:")
                                        || p2.equalsIgnoreCase("stderr") || p2.startsWith("|"))) {
                                    into.add(p2);
                                }
                            }
                        } catch (IOException e) {
                            log.debug("扫描配置{}失败: {}", p, e.getMessage());
                        }
                    });
        } catch (IOException e) {
            log.warn("扫描配置目录失败: {}", e.getMessage());
        }
    }

    private List<LogFile> buildList(Map<String, String> declared, Path base) {
        Map<String, LogFile> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : declared.entrySet()) {
            String srcPath = e.getKey();
            String source = e.getValue();
            Path resolved;
            if (Paths.get(srcPath).isAbsolute()) {
                resolved = Paths.get(srcPath);
            } else {
                resolved = base != null ? base.resolve(srcPath) : NginxProperties.confDirPath().resolve(srcPath);
            }
            String key = resolved.toString();
            if (!result.containsKey(key)) {
                result.put(key, toFile(key, resolved, source));
            }
        }
        // 已存在文件排前，不存在（未生成/路径改了还没写日志）排后
        List<LogFile> list = new ArrayList<>(result.values());
        list.sort((a, b) -> Boolean.compare(a.isExists(), b.isExists()));
        return list;
    }

    private LogFile toFile(String path, Path resolved, String source) {
        LogFile f = new LogFile();
        f.setPath(path);
        f.setDisplayPath(resolved.toString());
        f.setSource(source);
        try {
            if (Files.exists(resolved)) {
                f.setExists(true);
                f.setSize(Files.size(resolved));
                f.setLastModified(Files.getLastModifiedTime(resolved).toMillis());
                f.setName(resolved.getFileName() + "（" + source + "）");
            } else {
                f.setExists(false);
                f.setSize(0L);
                f.setName(resolved.getFileName() + "（" + source + "，文件未生成）");
            }
        } catch (IOException e) {
            f.setExists(false);
            f.setSize(0L);
        }
        return f;
    }

    /** 发现清单（含 .1 轮转变体）里是否允许读该路径。 */
    private boolean isAllowedPath(String requested) throws IOException {
        Set<String> allowed = new LinkedHashSet<>();
        for (LogFile f : discover()) {
            allowed.add(f.getPath());
            allowed.add(f.getPath() + ".1");
            allowed.add(f.getPath() + ".2");
        }
        return allowed.contains(requested);
    }

    private String normalizeReq(String path) {
        if (path == null || path.isBlank()) {
            throw new NginxPanelException("日志路径不能为空");
        }
        if (path.contains("..")) {
            throw new NginxPanelException("非法路径: " + path);
        }
        // 归一与发现清单一致的反斜杠分隔（Windows 上 Paths.toString 为反斜杠）
        return Paths.get(path).toString();
    }

    /** 从尾部高效读 N 行；文件超过阈值时从中间开始（truncated 置位）。 */
    private String tailNative(Path file, int lines, boolean[] truncated) throws IOException {
        if (!Files.exists(file)) {
            return "";
        }
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
            long size = raf.length();
            long start = Math.max(0, size - MAX_TAIL_BYTES);
            if (start > 0 && truncated != null) {
                truncated[0] = true;
            }
            raf.seek(start);
            byte[] data = new byte[(int) (size - start)];
            raf.readFully(data);
            String text = new String(data, StandardCharsets.UTF_8);
            String[] all = text.split("\n", -1);
            // 第一行可能是被截断的半行，去掉
            int from = start > 0 && all.length > 1 ? 1 : 0;
            int to = all.length;
            int tail = Math.min(lines, to - from);
            StringBuilder sb = new StringBuilder();
            if (start > 0 && all.length > 1 && truncated != null && truncated[0]) {
                sb.append("...(前面还有内容，仅显示尾部 ").append(lines).append(" 行)\n");
            }
            for (int i = to - tail; i < to; i++) {
                sb.append(all[i]);
                if (i < to - 1) {
                    sb.append('\n');
                }
            }
            return sb.toString();
        }
    }
}
