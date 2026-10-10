package yi.shi.plinth.ngx.dto;

import lombok.Data;

import java.util.Map;

/**
 * 应用模板生成配置文件请求。
 */
@Data
public class ApplyTemplateRequest {
    /** 模板名（不含扩展名） */
    private String template;
    /** 目标文件相对路径（conf.d/xxx.conf） */
    private String path;
    /** 模板变量替换表，key 为模板中的 {{KEY}} */
    private Map<String, String> vars;
    private String reason;
    private Boolean reloadAfter;
}
