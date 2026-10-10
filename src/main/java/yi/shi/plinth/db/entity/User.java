package yi.shi.plinth.db.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体。Nginx Shell 仅一个管理员账户，字段为最小集。
 */
@Data
public class User {
    private Long id;
    private String username;
    private String password;
    private String salt;
    /** 角色列表，逗号分隔，如 "admin,user" */
    private String roles;
    /** 1=正常 0=禁用 */
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
