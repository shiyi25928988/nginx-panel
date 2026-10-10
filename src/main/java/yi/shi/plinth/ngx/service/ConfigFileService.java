package yi.shi.plinth.ngx.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import yi.shi.plinth.ngx.NginxPanelException;
import yi.shi.plinth.ngx.NginxProperties;
import yi.shi.plinth.ngx.dto.FileContent;
import yi.shi.plinth.ngx.dto.FileNode;
import yi.shi.plinth.ngx.dto.SaveConfigRequest;
import yi.shi.plinth.ngx.dto.SaveResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 配置文件服务：目录浏览、读取、保存、删除。
 *
 * <p>核心安全策略：
 * <ul>
 *   <li>路径必须落在配置根目录（conf.dir）内，拒绝任何 ../ 越界与绝对路径注入。</li>
 *   <li>仅允许编辑 .conf / .types / mime.types；仅允许删除 .conf。</li>
 *   <li>写入使用同目录临时文件 + 原子 move，崩溃不会留下半份配置。</li>
 *   <li>保存流水线：写入 → nginx -t → 失败自动回滚原内容 → 记录版本与审计。</li>
 * </ul>
 */
@Slf4j
@Singleton
public class ConfigFileService {

    /** 单文件可读上限（2MB），防止误把超大文件灌进编辑器 */
    private static final long MAX_FILE_SIZE = 2 * 1024 * 1024;
    private static final Set<String> EDITABLE_SUFFIXES = Set.of(".conf", ".types");
    private static final String EDITABLE_NAME = "mime.types";
    /** 目录树最大深度 */
    private static final int MAX_DEPTH = 4;

    @Inject
    private NginxControlService nginxControlService;

    @Inject
    private VersionService versionService;

    @Inject
    private AuditService auditService;

    @Inject
    private TemplateService templateService;

    @Inject
    private yi.shi.plinth.user.UserService userService;

    /** 递归列出配置目录下全部文件与子目录（文件在前、按路径排序）。 */
    public List<FileNode> listFiles() {
        Path root = confDir();
        try (Stream<Path> stream = Files.walk(root, MAX_DEPTH)) {
            return stream.filter(p -> !p.equals(root))
                    .map(this::toNode)
                    .sorted(Comparator.comparing(FileNode::getPath))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new NginxPanelException("读取配置目录失败: " + e.getMessage(), e);
        }
    }

    /** 读取文件内容（UTF-8）。 */
    public FileContent read(String path) {
        Path target = resolveEditable(path, false);
        if (!Files.exists(target)) {
            throw new NginxPanelException("文件不存在: " + path);
        }
        try {
            long size = Files.size(target);
            if (size > MAX_FILE_SIZE) {
                throw new NginxPanelException("文件超过 2MB，不支持在线编辑: " + path);
            }
            String content = Files.readString(target, StandardCharsets.UTF_8);
            return new FileContent(path, content, isEditable(path), size, lastModified(target),
                    (int) versionService.listByPath(path, 1, 1).getTotal());
        } catch (IOException e) {
            throw new NginxPanelException("读取文件失败: " + e.getMessage(), e);
        }
    }

    /**
     * 保存流水线：写入 → nginx -t 校验 → 失败回滚 → 成功记录版本 + 审计 → 可选 reload。
     * 新建文件时 oldContent 为 null；校验失败则删除刚写入的文件。
     */
    public SaveResult save(SaveConfigRequest req) {
        String reason = req.getReason() == null || req.getReason().isBlank() ? "Web 控制台编辑" : req.getReason();
        return writePipeline("SAVE_CONFIG", req.getPath(), req.getContent(), reason, req.getReloadAfter());
    }

    /** 应用模板：渲染变量后走保存流水线（目标文件已存在则等效于覆盖保存，也会被校验保护）。 */
    public SaveResult applyTemplate(String template, String path, java.util.Map<String, String> vars,
                                    String reason, Boolean reloadAfter) {
        String rendered = templateService.render(template, vars);
        String lsReason = (reason == null || reason.isBlank() ? "应用模板" : reason) + " " + template;
        return writePipeline("APPLY_TEMPLATE", path, rendered, lsReason, reloadAfter);
    }

    @Inject
    private ConfWriteLock confWriteLock;

    /**
     * 在线校验候选内容（任意可编辑文件，不落盘）：
     * 临时把候选内容原子写入目标文件的真实位置 → {@code nginx -t} 整树校验
     * （目标文件经 include 以生产一致性语义参与校验，绝对/相对 include、
     * docker 共享卷均天然正确）→ finally 原子恢复原文件（原不存在则删除）。
     *
     * <p>局限：仅当目标文件被主配置 include（conf.d/*.conf、nginx.conf 等）
     * 时其内容才会参与校验；未被 include 的文件（如 sites-available 未链接）校验不到。
     * content 为 null 表示校验磁盘当前内容，直接走整体 nginx -t，无需暂改文件。
     */
    public SaveResult validateCandidate(String path, String content) {
        Path target = resolveEditable(path, true);
        SaveResult result = new SaveResult();
        synchronized (confWriteLock.monitor) {
            String oldContent = exists(target) ? readString(target) : null;
            try {
                if (content == null || content.equals(oldContent)) {
                    // 磁盘内容即为候选，无需暂改文件
                    var test = nginxControlService.test();
                    return finishValidate(result, path, "磁盘当前版本", test);
                }
                atomicWrite(target, content);
                var test = nginxControlService.test();
                return finishValidate(result, path, oldContent != null
                        ? "编辑器候选内容（校验期间临时替换文件，已还原）" : "新建文件候选内容（校验期间临时创建，已删除）",
                        test);
            } catch (IOException e) {
                auditService.failed("VALIDATE_CONFIG", path, "候选内容写入失败: " + e.getMessage());
                throw new NginxPanelException("候选内容写入失败: " + e.getMessage(), e);
            } finally {
                // 无论成败都恢复磁盘原状
                try {
                    if (oldContent != null) {
                        atomicWrite(target, oldContent);
                    } else {
                        Files.deleteIfExists(target);
                    }
                } catch (IOException e) {
                    log.error("校验后恢复原文件失败: {}", target, e);
                    auditService.failed("VALIDATE_CONFIG", path, "恢复原文件失败，请检查 " + path);
                }
            }
        }
    }

    private SaveResult finishValidate(SaveResult result, String path, String what,
                                      yi.shi.plinth.ngx.dto.ExecResult test) {
        result.setSuccess(test.isSuccess());
        result.setMessage((test.isSuccess() ? "校验通过（未写入磁盘，" : "校验失败（未写入磁盘，") + what + "）");
        result.setTestOutput(test.getOutput());
        if (test.isSuccess()) {
            auditService.success("VALIDATE_CONFIG", path, "在线候选校验通过（" + what + "）");
        } else {
            auditService.failed("VALIDATE_CONFIG", path,
                    "在线候选校验失败（" + what + "）\n" + test.getOutput());
        }
        return result;
    }

    /**
     * 上传文件整体覆盖（或新建）：危险操作，先校验管理员当前密码，
     * 之后与保存/回滚走同一条校验流水线，diff 由版本记录自动可见。
     */
    public SaveResult upload(String path, String content, String password,
                             String reason, Boolean reloadAfter) {
        Long loginId;
        try {
            loginId = cn.dev33.satoken.stp.StpUtil.getLoginIdAsLong();
        } catch (Exception e) {
            throw new NginxPanelException("会话已过期，请重新登录");
        }
        if (password == null || password.isBlank()) {
            throw new NginxPanelException("需要输入管理员密码确认此操作");
        }
        if (!userService.verifyPassword(loginId, password)) {
            auditService.failed("UPLOAD_CONFIG", path, "密码确认失败，上传被拒绝");
            throw new NginxPanelException("密码错误，操作已拒绝");
        }
        String lsReason = (reason == null || reason.isBlank()
                ? "上传整体覆盖" : reason);
        auditService.success("UPLOAD_CONFIG", path, "密码确认通过，开始覆盖流程");
        return writePipeline("UPLOAD_CONFIG", path, content, lsReason, reloadAfter);
    }

    /** 删除配置文件（仅 .conf），删除前 nginx -t 会因文件缺失? —— 先删再校验，失败回滚。 */
    public void delete(String path) {
        Path target = resolveEditable(path, true);
        if (!isConf(path)) {
            throw new NginxPanelException("只允许删除 .conf 文件");
        }
        if (!exists(target)) {
            throw new NginxPanelException("文件不存在: " + path);
        }
        String oldContent = readString(target);
        try {
            Files.delete(target);
        } catch (IOException e) {
            throw new NginxPanelException("删除失败: " + e.getMessage(), e);
        }
        var test = nginxControlService.test();
        if (!test.isSuccess()) {
            // 删除后配置校验失败：恢复文件
            try {
                atomicWrite(target, oldContent);
            } catch (IOException e) {
                throw new NginxPanelException("删除后校验失败且回滚失败，请手动恢复: " + path, e);
            }
            auditService.failed("DELETE_CONFIG", path, "删除后 nginx -t 失败，已恢复文件\n" + test.getOutput());
            throw new NginxPanelException("删除后 nginx -t 校验失败（该文件被主配置 include），已自动恢复");
        }
        versionService.record(path, oldContent, null, "删除文件（版本记录 new_content 为空表示删除）");
        auditService.success("DELETE_CONFIG", path, "删除成功，nginx -t 校验通过");
    }

    /** 是否可编辑：.conf / .types / mime.types。 */
    public boolean isEditable(String path) {
        String name = fileName(path).toLowerCase();
        return name.endsWith(".conf") || name.endsWith(".types") || name.equalsIgnoreCase(EDITABLE_NAME);
    }

    public boolean isConf(String path) {
        return fileName(path).toLowerCase().endsWith(".conf");
    }

    // ---------- 内部 ----------

    /** 写入流水线主体（save / rollback / applyTemplate 共用）。 */
    private SaveResult writePipeline(String action, String path, String content,
                                     String reason, Boolean reloadAfter) {
        // 与 validateCandidate 共用 ioLock：写磁盘/暂改文件的操作串行化
        synchronized (confWriteLock.monitor) {
            return doWritePipeline(action, path, content, reason, reloadAfter);
        }
    }

    private SaveResult doWritePipeline(String action, String path, String content,
                                       String reason, Boolean reloadAfter) {
        Path target = resolveEditable(path, true);
        if (Files.isDirectory(target)) {
            throw new NginxPanelException("目标是目录: " + path);
        }
        String oldContent = exists(target) ? readString(target) : null;
        boolean contentUnchanged = oldContent != null && oldContent.equals(content);

        SaveResult result = new SaveResult();
        if (contentUnchanged) {
            result.setSuccess(true);
            String skipHint = "内容未变化，跳过写入与校验";
            result.setMessage(skipHint);
            auditService.success(action, path, "无差异跳过：" + skipHint);
            return result;
        }
        try {
            atomicWrite(target, content);
        } catch (IOException e) {
            auditService.failed(action, path, "写入失败: " + e.getMessage());
            throw new NginxPanelException("写入文件失败: " + e.getMessage(), e);
        }

        // 校验；失败则回滚原内容（新文件则删除）
        var test = nginxControlService.test();
        if (!test.isSuccess()) {
            rollbackFile(target, oldContent);
            String label = action.equals("APPLY_TEMPLATE") ? "应用模板" : "保存";
            String detail = label + "后 nginx -t 校验失败，已" + (oldContent != null ? "回滚原内容" : "删除新文件")
                    + "\n" + test.getOutput();
            auditService.failed(action, path, detail);
            result.setSuccess(false);
            result.setMessage(label + "已撤销（nginx -t 校验失败，" + (oldContent != null ? "已恢复原内容" : "已删除新文件") + "）");
            result.setTestOutput(test.getOutput());
            return result;
        }

        // 记录版本 + 审计
        long versionId = versionService.record(path, oldContent, content, reason);
        auditService.success(action, path, "成功，版本 #" + versionId + "，原因: " + reason);
        result.setSuccess(true);
        result.setMessage("成功（nginx -t 校验通过），版本 #" + versionId + "，原因: " + reason);
        result.setTestOutput(test.getOutput());
        result.setVersionId(versionId);

        if (Boolean.TRUE.equals(reloadAfter)) {
            applyReload(result);
        }
        return result;
    }

    private void applyReload(SaveResult result) {
        try {
            var reload = nginxControlService.reload();
            if (reload.isSuccess()) {
                result.setReloaded(true);
                result.setReloadOutput(reload.getOutput());
                result.setMessage(result.getMessage() + "，并已重载配置");
                auditService.success("RELOAD", null, "保存后自动 reload 成功");
            } else {
                result.setMessage(result.getMessage() + "，但重载失败：" + reload.getOutput());
                auditService.failed("RELOAD", null, "保存后自动 reload 失败: " + reload.getOutput());
            }
        } catch (NginxPanelException e) {
            result.setMessage(result.getMessage() + "，但重载失败：" + e.getMessage());
            auditService.failed("RELOAD", null, "保存后自动 reload 失败: " + e.getMessage());
        }
    }

    /** 同目录临时文件 + 原子 move 写入。 */
    private void atomicWrite(Path target, String content) throws IOException {
        Path tmp = target.resolveSibling(target.getFileName().toString() + "." + UUID.randomUUID() + ".tmp");
        Files.writeString(tmp, content, StandardCharsets.UTF_8);
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            // 部分文件系统不支持 ATOMIC_MOVE，退化为普通替换
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void rollbackFile(Path target, String oldContent) {
        try {
            if (oldContent != null) {
                atomicWrite(target, oldContent);
            } else {
                Files.deleteIfExists(target);
            }
        } catch (IOException e) {
            log.error("回滚失败: {}", target, e);
        }
    }

    /** 路径安全解析：拒绝越界；editable=true 时限制为可编辑文件。 */
    private Path resolveEditable(String path, boolean editable) {
        if (path == null || path.isBlank()) {
            throw new NginxPanelException("文件路径不能为空");
        }
        if (path.contains("..")) {
            throw new NginxPanelException("非法路径: " + path);
        }
        if (path.startsWith("/") || path.contains("\\")) {
            // 统一为相对路径（以 / 开头的先去掉）
            path = path.replace('\\', '/');
            while (path.startsWith("/")) {
                path = path.substring(1);
            }
            if (Paths.get(path).isAbsolute()) {
                throw new NginxPanelException("非法路径: " + path);
            }
        }
        if (editable && !isEditable(path)) {
            throw new NginxPanelException("该文件类型不允许在线修改: " + path);
        }
        Path root = confDir().toAbsolutePath().normalize();
        Path target = root.resolve(path).normalize();
        if (!target.startsWith(root)) {
            throw new NginxPanelException("路径超出配置目录范围: " + path);
        }
        return target;
    }

    private FileNode toNode(Path p) {
        Path root = confDir();
        String rel = root.relativize(p).toString().replace('\\', '/');
        boolean dir = Files.isDirectory(p);
        FileNode node = new FileNode();
        node.setPath(dir ? rel + "/" : rel);
        node.setName(p.getFileName().toString());
        node.setDirectory(dir);
        node.setEditable(!dir && isEditable(rel));
        try {
            BasicFileAttributes attrs = Files.readAttributes(p, BasicFileAttributes.class);
            node.setSize(dir ? 0 : attrs.size());
            node.setLastModified(attrs.lastModifiedTime().toMillis());
        } catch (IOException e) {
            node.setSize(0);
        }
        return node;
    }

    private String readString(Path target) {
        try {
            return Files.readString(target, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new NginxPanelException("读取文件失败: " + e.getMessage(), e);
        }
    }

    private boolean exists(Path p) {
        return Files.exists(p) && Files.isRegularFile(p);
    }

    private Long lastModified(Path p) {
        try {
            return Files.getLastModifiedTime(p).toMillis();
        } catch (IOException e) {
            return null;
        }
    }

    private String fileName(String path) {
        return Paths.get(path.replace('\\', '/')).getFileName().toString();
    }

    private Path confDir() {
        return NginxProperties.confDirPath();
    }
}
