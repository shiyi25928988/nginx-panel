package yi.shi.plinth.user;

import cn.dev33.satoken.stp.StpUtil;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import yi.shi.plinth.db.entity.User;
import yi.shi.plinth.db.mapper.UserMapper;
import yi.shi.plinth.auth.AuthHelper;
import yi.shi.plinth.utils.PasswordEncoder;

import java.time.LocalDateTime;

/**
 * 用户业务服务：Nginx Shell 只有一个管理员，仅保留登录 / 登出 / 当前用户 / 改密码。
 *
 * <p>角色以逗号分隔字符串存库，登录时拆分为数组交给 {@link AuthHelper} 写入内存。
 * 对外返回的 {@link User} 一律脱敏（清空 password / salt）。
 */
@Singleton
public class UserService {

    @Inject
    private UserMapper userMapper;

    /** 登录：校验账号密码与状态，成功则写入 sa-token 会话与角色，返回 token。 */
    public String login(String username, String password) {
        User user = userMapper.findByUsername(username);
        if (user == null || !PasswordEncoder.verify(password, user.getSalt(), user.getPassword())) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new IllegalStateException("account disabled");
        }
        AuthHelper.login(user.getId(), splitRoles(user.getRoles()));
        return StpUtil.getTokenValue();
    }

    /** 登出当前会话。 */
    public void logout() {
        AuthHelper.logout();
    }

    /** 获取当前登录用户（脱敏）。 */
    public User currentUser() {
        return sanitize(userMapper.findById(StpUtil.getLoginIdAsLong()));
    }

    /** 校验某用户当前密码（危险操作二次确认用）。 */
    public boolean verifyPassword(Long id, String password) {
        User user = userMapper.findById(id);
        return user != null && PasswordEncoder.verify(password, user.getSalt(), user.getPassword());
    }

    /** 修改密码：校验旧密码后重置 salt 与 hash。 */
    public void changePassword(Long id, String oldPassword, String newPassword) {
        User user = userMapper.findById(id);
        if (user == null) {
            throw new IllegalArgumentException("user not found");
        }
        if (!PasswordEncoder.verify(oldPassword, user.getSalt(), user.getPassword())) {
            throw new IllegalArgumentException("old password incorrect");
        }
        String salt = PasswordEncoder.generateSalt();
        user.setSalt(salt);
        user.setPassword(PasswordEncoder.hash(newPassword, salt));
        user.setUpdateTime(LocalDateTime.now());
        userMapper.update(user);
    }

    private User sanitize(User user) {
        if (user != null) {
            user.setPassword(null);
            user.setSalt(null);
        }
        return user;
    }

    private String[] splitRoles(String roles) {
        if (roles == null || roles.isBlank()) {
            return new String[0];
        }
        return java.util.Arrays.stream(roles.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
    }
}
