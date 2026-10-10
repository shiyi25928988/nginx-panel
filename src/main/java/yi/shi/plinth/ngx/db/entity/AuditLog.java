package yi.shi.plinth.ngx.db.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审计日志：记录登录/保存/校验/重载/回滚等全部操作。
 */
@Data
public class AuditLog {
    private Long id;
    private String username;
    /**
     * 动作：LOGIN / LOGOUT / SAVE_CONFIG / CREATE_CONFIG / DELETE_CONFIG /
     * ROLLBACK_CONFIG / APPLY_TEMPLATE / TEST_CONFIG / RELOAD / CHANGE_PASSWORD
     */
    private String action;
    private String filePath;
    private String detail;
    /** SUCCESS / FAILED */
    private String result;
    private String clientIp;
    private LocalDateTime createTime;
}
