package yi.shi.plinth.ngx.api;

import com.google.inject.Inject;
import yi.shi.plinth.annotation.auth.AUTH;
import yi.shi.plinth.annotation.http.HttpParam;
import yi.shi.plinth.annotation.http.HttpPath;
import yi.shi.plinth.annotation.http.HttpService;
import yi.shi.plinth.annotation.http.Method.GET;
import yi.shi.plinth.http.result.JSON;
import yi.shi.plinth.ngx.db.entity.AuditLog;
import yi.shi.plinth.ngx.dto.PageData;
import yi.shi.plinth.ngx.service.AuditService;

/**
 * 审计日志接口：分页查询（可选按动作过滤）。
 */
@HttpService
public class AuditApi {

    @Inject
    private AuditService auditService;

    @GET
    @HttpPath("/api/logs")
    @AUTH(orRole = "admin")
    public JSON<PageData<AuditLog>> logs(@HttpParam("action") String action,
                                        @HttpParam("page") String page,
                                        @HttpParam("size") String size) {
        return new JSON<>(auditService.list(action, parseInt(page, 1), parseInt(size, 10)));
    }

    private static int parseInt(String value, int defaultValue) {
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            return defaultValue;
        }
    }
}
