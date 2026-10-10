package yi.shi.plinth.ngx.dto;

import lombok.Data;

/**
 * 替换证书请求。
 */
@Data
public class ReplaceCertRequest {
    /** 目标证书文件路径（来自证书发现清单，或 confDir/certs/ 下新文件） */
    private String certPath;
    /** 新证书 PEM（可含完整链） */
    private String certPem;
    /** 新私钥 PEM（PKCS8/RSA/EC 无口令格式；留空=仅换证书，需与现有私钥匹配） */
    private String keyPem;
    /** 管理员密码二次确认 */
    private String password;
    private String reason;
    private Boolean reloadAfter;
}
