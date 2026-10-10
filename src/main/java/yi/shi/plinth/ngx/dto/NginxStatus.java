package yi.shi.plinth.ngx.dto;

import lombok.Data;

/**
 * Nginx 运行状态。
 */
@Data
public class NginxStatus {
    /** nginx 版本号，如 "nginx/1.24.0"；不可用时为错误说明 */
    private String version;
    /** 配置根目录 */
    private String confDir;
    private String execMode;
    /** docker 容器名（docker 模式） */
    private String container;
    /** 是否探测到运行中（native：pid 文件+进程存活；docker：容器 running） */
    private Boolean running;
    /** master 进程 pid（native 且配置了 pid 文件时） */
    private Long pid;
    /** 最后一次 nginx -t 的时间戳（epoch millis），null 表示本会话未执行过 */
    private Long lastTestTime;
    /** 最后一次 nginx -t 是否通过 */
    private Boolean lastTestOk;
}
