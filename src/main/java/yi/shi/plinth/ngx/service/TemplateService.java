package yi.shi.plinth.ngx.service;

import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import yi.shi.plinth.ngx.NginxPanelException;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 内嵌配置模板服务：模板存放在 jar 内 /templates/*.conf，含 {{VAR}} 占位符。
 *
 * <p>注册表固定（新增模板：加资源文件 + 在 {@link #TEMPLATES} 登记元数据）。
 */
@Slf4j
@Singleton
public class TemplateService {

    /** 内嵌模板元数据注册表。 */
    public record TemplateMeta(String name, String title, String description, String targetFile) {
    }

    private static final List<TemplateMeta> TEMPLATES = List.of(
            new TemplateMeta("nginx-conf", "主配置 nginx.conf",
                    "完整主配置模板：全局参数 + 事件块 + http 块（日志/gzip/默认站点 include）", "nginx.conf"),
            new TemplateMeta("static-site", "静态网站",
                    "静态站点：root + index + 404 页 + 静态资源缓存 + 拒绝隐藏文件", "conf.d/static-site.conf"),
            new TemplateMeta("reverse-proxy", "反向代理",
                    "HTTP 反向代理：proxy_pass + 常用透传头 + 超时调优", "conf.d/proxy.conf"),
            new TemplateMeta("reverse-proxy-ws", "反向代理（WebSocket）",
                    "带 WebSocket 升级头的反向代理，适合 IM/长连接应用", "conf.d/proxy-ws.conf"),
            new TemplateMeta("load-balancer", "负载均衡",
                    "upstream 多后端轮询 + 备用节点 + 代理站点", "conf.d/lb.conf"),
            new TemplateMeta("ssl-https", "HTTPS + HTTP 跳转",
                    "SSL 站点：证书路径 + HTTP 80 跳转 HTTPS + 安全头", "conf.d/ssl.conf"),
            new TemplateMeta("gzip-security", "Gzip + 安全头（公共片段）",
                    "可 include 的公共片段：gzip 压缩 + 常见安全响应头", "conf.d/gzip-security.conf"),
            new TemplateMeta("cache-frontend", "静态资源缓存",
                    "前端资源强缓存：图片/JS/CSS 按类型设置 expires 与 Cache-Control", "conf.d/cache.conf")
    );

    /** {{VAR}} 占位符 */
    private static final Pattern VAR_PATTERN = Pattern.compile("\\{\\{([A-Za-z0-9_]+)\\}\\}");

    /** 模板元数据列表。 */
    public List<TemplateMeta> list() {
        return TEMPLATES;
    }

    /** 读取模板原文（保留 {{VAR}} 占位符，供前端展示与预填变量）。 */
    public String raw(String name) {
        return loadResource(name);
    }

    /** 提取模板中的占位符变量名列表（保持出现顺序）。 */
    public List<String> variables(String name) {
        String raw = loadResource(name);
        List<String> vars = new ArrayList<>();
        var matcher = VAR_PATTERN.matcher(raw);
        while (matcher.find()) {
            String v = matcher.group(1);
            if (!vars.contains(v)) {
                vars.add(v);
            }
        }
        return vars;
    }

    /** 渲染模板：替换 {{VAR}}；出现未提供的变量则报错，避免把占位符写进 nginx 配置。 */
    public String render(String name, Map<String, String> vars) {
        String raw = loadResource(name);
        var matcher = VAR_PATTERN.matcher(raw);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String value = vars == null ? null : vars.get(matcher.group(1));
            if (value == null || value.isBlank()) {
                throw new NginxPanelException("模板变量未提供: {{"
                        + matcher.group(1) + "}}（模板 " + name + "）");
            }
            matcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(value));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String loadResource(String name) {
        // 防路径注入：只允许字母数字下划线中划线
        if (name == null || !name.matches("[A-Za-z0-9_-]+")) {
            throw new NginxPanelException("非法模板名: " + name);
        }
        String resourcePath = "/templates/" + name + ".conf";
        try (InputStream in = TemplateService.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new NginxPanelException("模板不存在: " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (NginxPanelException e) {
            throw e;
        } catch (Exception e) {
            throw new NginxPanelException("读取模板失败: " + name, e);
        }
    }
}
