package yi.shi.plinth.ngx.dto;

import lombok.Data;

import java.util.List;

/**
 * 服务状态总览（服务状态面板数据源）。
 */
@Data
public class ServiceStatus {
    // ---- Nginx ----
    private NginxStatus nginx;
    /** nginx -V 完整版本（含编译参数）；失败时为错误说明 */
    private String fullVersion;
    /** 探测到的 nginx 进程（native 模式） */
    private List<ProcInfo> processes;
    /** master 运行时长文本（如 "3d 04:12:53"） */
    private String masterUptime;

    // ---- 宿主机 ----
    private String osName;
    private String jvmVersion;
    /** 面板运行时长秒数 */
    private long panelUptimeSeconds;
    /** 系统 CPU 负载 0-100（-1 表示不可用） */
    private double cpuLoad;
    /** 系统内存字节 */
    private Long memTotal;
    private Long memUsed;
    /** 面板 JVM 堆使用 */
    private Long jvmHeapUsed;
    private Long jvmHeapMax;
    private List<DiskInfo> disks;
}
