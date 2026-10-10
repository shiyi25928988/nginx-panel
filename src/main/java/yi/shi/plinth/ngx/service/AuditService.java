package yi.shi.plinth.ngx.service;

import cn.dev33.satoken.stp.StpUtil;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import yi.shi.plinth.ngx.db.entity.AuditLog;
import yi.shi.plinth.user.CurrentUser;
import yi.shi.plinth.ngx.db.mapper.AuditLogMapper;
import yi.shi.plinth.ngx.dto.PageData;
import yi.shi.plinth.servlet.ServletHelper;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 审计日志服务：所有敏感操作（登录/保存/校验/重载/回滚/删除）统一落库。
 */
@Slf4j
@Singleton
public class AuditService {

    public static final String RESULT_SUCCESS = "SUCCESS";
    public static final String RESULT_FAILED = "FAILED";

    @Inject
    private AuditLogMapper auditLogMapper;

    @Inject
    private CurrentUser currentUser;

    /** 记录一条审计日志（自动带当前用户与客户端 IP；未登录场景用户名取 "anonymous"）。 */
    public void log(String action, String filePath, String detail, String result) {
        try {
            AuditLog entry = new AuditLog();
            entry.setUsername(username());
            entry.setAction(action);
            entry.setFilePath(filePath);
            entry.setDetail(detail);
            entry.setResult(result);
            entry.setClientIp(clientIp());
            entry.setCreateTime(LocalDateTime.now());
            auditLogMapper.insert(entry);
        } catch (Exception e) {
            // 审计失败不影响主流程，但必须留痕在应用日志
            log.error("审计日志写入失败: {}", e.getMessage());
        }
    }

    public void success(String action, String filePath, String detail) {
        log(action, filePath, detail, RESULT_SUCCESS);
    }

    public void failed(String action, String filePath, String detail) {
        log(action, filePath, detail, RESULT_FAILED);
    }

    /** 分页查询审计日志（可选按动作过滤）。 */
    public PageData<AuditLog> list(String action, int page, int size) {
        int offset = (page - 1) * size;
        List<AuditLog> logs;
        long total;
        if (action == null || action.isBlank()) {
            logs = auditLogMapper.findPage(offset, size);
            total = auditLogMapper.count();
        } else {
            logs = auditLogMapper.findPageByAction(action, offset, size);
            total = auditLogMapper.countByAction(action);
        }
        return new PageData<>(logs, total, page, size);
    }

    public List<AuditLog> recent(int limit) {
        return auditLogMapper.findRecent(limit);
    }

    private String username() {
        return currentUser.username();
    }

    /** 客户端 IP：优先 X-Forwarded-For（经 nginx 反代部署面板时），否则 remoteAddr。 */
    private String clientIp() {
        try {
            HttpServletRequest req = ServletHelper.getRequest();
            String xff = req.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                return xff.split(",")[0].trim();
            }
            return req.getRemoteAddr();
        } catch (Exception e) {
            return null;
        }
    }
}
