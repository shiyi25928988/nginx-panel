package yi.shi.plinth.ngx.dto;

import lombok.Data;

/**
 * 自签证书生成请求。
 */
@Data
public class GenerateCertRequest {
    /** 文件名（不含扩展名），输出 <name>.crt / <name>.key 到 confDir/certs/ */
    private String name;
    private String cn;
    /** SAN 域名列表（逗号分隔；不含 CN 时自动加入 CN） */
    private String sans;
    /** 自签有效天数，默认 3650 */
    private Integer days;
    /** 覆盖已有同名文件时必填的密码 */
    private String password;
    private String reason;
}
