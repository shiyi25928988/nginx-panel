package yi.shi.plinth.ngx.dto;

import lombok.Data;

/**
 * 上传配置文件（整体覆盖/新建）请求。
 */
@Data
public class UploadConfigRequest {
    /** 目标文件相对 confDir 的路径（如 nginx.conf 或 conf.d/xxx.conf） */
    private String path;
    /** 上传文件内容（前端 FileReader 读取为 UTF-8 文本） */
    private String content;
    /** 操作者当前密码（危险操作二次确认） */
    private String password;
    private String reason;
    private Boolean reloadAfter;
}
