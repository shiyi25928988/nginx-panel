package yi.shi.plinth.ngx.dto;

import lombok.Data;

/**
 * 版本回滚请求。
 */
@Data
public class RollbackRequest {
    private Long versionId;
    private String reason;
    private Boolean reloadAfter;
}
