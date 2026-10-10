package yi.shi.plinth.ngx.dto;

import lombok.Data;

/**
 * 保存配置文件结果。
 */
@Data
public class SaveResult {
    private boolean success;
    private String message;
    /** nginx -t 输出（失败时为错误输出） */
    private String testOutput;
    /** 是否已自动 reload */
    private boolean reloaded;
    /** reload 输出 */
    private String reloadOutput;
    /** 本次保存生成的版本 id（校验失败回滚则无） */
    private Long versionId;
}
