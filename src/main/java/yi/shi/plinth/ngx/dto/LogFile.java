package yi.shi.plinth.ngx.dto;

import lombok.Data;

/**
 * 被发现的 nginx 日志文件。
 */
@Data
public class LogFile {
    /** 拉取日志时回传的 key（解析后的绝对路径字符串） */
    private String path;
    /** 展示名：日志文件名 + 来源标注 */
    private String name;
    /** 解析后的绝对/容器内路径 */
    private String displayPath;
    /** 文件当前是否存在（reload 后新路径文件可能还没建立） */
    private boolean exists;
    private Long size;
    private Long lastModified;
    /** 来源：config=配置指令声明 / default=nginx -v 编译默认 */
    private String source;
}
