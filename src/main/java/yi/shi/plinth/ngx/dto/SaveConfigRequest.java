package yi.shi.plinth.ngx.dto;

import lombok.Data;

/**
 * 保存配置文件请求。
 */
@Data
public class SaveConfigRequest {
    /** 相对 confDir 的路径 */
    private String path;
    /** 新内容 */
    private String content;
    /** 修改原因（记录到版本与审计） */
    private String reason;
    /** 保存成功后是否自动 reload */
    private Boolean reloadAfter;
}
