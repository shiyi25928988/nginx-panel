package yi.shi.plinth.user;

import cn.dev33.satoken.stp.StpUtil;
import com.google.inject.Inject;
import yi.shi.plinth.db.entity.User;
import yi.shi.plinth.annotation.auth.AUTH;
import yi.shi.plinth.annotation.http.HttpBody;
import yi.shi.plinth.annotation.http.HttpPath;
import yi.shi.plinth.annotation.http.HttpService;
import yi.shi.plinth.annotation.http.Method.GET;
import yi.shi.plinth.annotation.http.Method.POST;
import yi.shi.plinth.http.result.JSON;
import yi.shi.plinth.ngx.service.AuditService;
import yi.shi.plinth.servlet.ServletHelper;
import yi.shi.plinth.user.dto.ChangePasswordRequest;
import yi.shi.plinth.user.dto.LoginRequest;

/**
 * 用户模块 HTTP 接口：登录 / 登出 / 当前用户 / 改密码（单管理员，无注册）。
 * 登录与改密码均写入审计日志。
 */
@HttpService
public class UserApi {

    @Inject
    private UserService userService;

    @Inject
    private AuditService auditService;

    @POST
    @HttpPath("/user/login")
    public JSON<java.util.Map<String, String>> login(@HttpBody LoginRequest req) {
        try {
            String token = userService.login(req.getUsername(), req.getPassword());
            auditService.success("LOGIN", null, "用户 " + req.getUsername() + " 登录成功");
            return new JSON<>(java.util.Map.of("token", token));
        } catch (Exception e) {
            auditService.failed("LOGIN", null, "用户 " + req.getUsername() + " 登录失败: " + e.getMessage());
            throw e;
        }
    }

    @GET
    @HttpPath("/user/logout")
    public JSON<String> logout() throws java.io.IOException {
        auditService.success("LOGOUT", null, "用户登出");
        userService.logout();
        ServletHelper.getResponse().sendRedirect("/page/login");
        return new JSON<>("logged out");
    }

    @GET
    @HttpPath("/user/current")
    @AUTH
    public JSON<User> current() {
        return new JSON<>(userService.currentUser());
    }

    @POST
    @HttpPath("/user/password")
    @AUTH
    public JSON<String> changePassword(@HttpBody ChangePasswordRequest req) {
        try {
            userService.changePassword(StpUtil.getLoginIdAsLong(), req.getOldPassword(), req.getNewPassword());
            auditService.success("CHANGE_PASSWORD", null, "修改密码成功，所有会话需重新登录");
            // 改密后强制重新登录：注销当前会话
            userService.logout();
            try {
                ServletHelper.getResponse().sendRedirect("/page/login");
            } catch (java.io.IOException ignored) {
                // sendRedirect 失败时 JSON 返回仍可用
            }
            return new JSON<>("password changed");
        } catch (Exception e) {
            auditService.failed("CHANGE_PASSWORD", null, "修改密码失败: " + e.getMessage());
            throw e;
        }
    }
}
