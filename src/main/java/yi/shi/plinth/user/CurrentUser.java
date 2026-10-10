package yi.shi.plinth.user;

import cn.dev33.satoken.stp.StpUtil;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import yi.shi.plinth.db.mapper.UserMapper;

/**
 * 当前会话用户工具：audit / 版本记录统一取"用户名字符串"而非 loginId。
 */
@Singleton
public class CurrentUser {

    public static final String ANONYMOUS = "anonymous";

    @Inject
    private UserMapper userMapper;

    /** 当前登录用户名；未登录/异常时返回 anonymous。 */
    public String username() {
        Object loginId;
        try {
            loginId = StpUtil.getLoginIdDefaultNull();
        } catch (Exception e) {
            return ANONYMOUS;
        }
        if (loginId == null) {
            return ANONYMOUS;
        }
        try {
            var user = userMapper.findById(Long.parseLong(loginId.toString()));
            return user != null ? user.getUsername() : loginId.toString();
        } catch (Exception e) {
            return loginId.toString();
        }
    }
}
