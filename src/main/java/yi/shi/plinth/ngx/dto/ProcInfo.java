package yi.shi.plinth.ngx.dto;

import lombok.Data;

/**
 * nginx 进程快照（native 模式经 ProcessHandle 探测；docker 模式为空）。
 */
@Data
public class ProcInfo {
    private Long pid;
    /** 进程命令行（master vs worker 靠参数区分） */
    private String command;
    /** 进程启动时间（epoch millis） */
    private Long startedAt;
    /** 是否 master（命令行含 "master process" 字样） */
    private boolean master;
}
