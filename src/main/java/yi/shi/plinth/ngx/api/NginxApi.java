package yi.shi.plinth.ngx.api;

import com.google.inject.Inject;
import cn.dev33.satoken.stp.StpUtil;
import yi.shi.plinth.annotation.auth.AUTH;
import yi.shi.plinth.annotation.http.HttpPath;
import yi.shi.plinth.annotation.http.HttpService;
import yi.shi.plinth.annotation.http.Method.GET;
import yi.shi.plinth.annotation.http.Method.POST;
import yi.shi.plinth.http.result.JSON;
import yi.shi.plinth.ngx.dto.ExecResult;
import yi.shi.plinth.ngx.dto.NginxStatus;
import yi.shi.plinth.ngx.service.AuditService;
import yi.shi.plinth.ngx.service.NginxControlService;

/**
 * Nginx 控制接口：状态 / 校验 / 重载。单管理员部署，所有接口要求登录 + admin 角色。
 */
@HttpService
public class NginxApi {

    @Inject
    private NginxControlService nginxControlService;

    @Inject
    private AuditService auditService;

    @GET
    @HttpPath("/api/nginx/status")
    @AUTH(orRole = "admin")
    public JSON<NginxStatus> status() {
        return new JSON<>(nginxControlService.status());
    }

    /** 手动触发 nginx -t。 */
    @POST
    @HttpPath("/api/nginx/test")
    @AUTH(orRole = "admin")
    public JSON<ExecResult> test() {
        ExecResult result = nginxControlService.test();
        auditService.success("TEST_CONFIG", null, result.isSuccess()
                ? "nginx -t 校验通过" : "nginx -t 校验失败\n" + result.getOutput());
        return new JSON<>(result);
    }

    /** 手动触发 nginx -s reload。 */
    @POST
    @HttpPath("/api/nginx/reload")
    @AUTH(orRole = "admin")
    public JSON<ExecResult> reload() {
        ExecResult result;
        try {
            result = nginxControlService.reload();
        } catch (Exception e) {
            auditService.failed("RELOAD", null, "重载失败: " + e.getMessage());
            throw e;
        }
        if (result.isSuccess()) {
            auditService.success("RELOAD", null, "重载成功");
        } else {
            auditService.failed("RELOAD", null, "重载失败，退出码 " + result.getExitCode() + "\n" + result.getOutput());
        }
        return new JSON<>(result);
    }
}
