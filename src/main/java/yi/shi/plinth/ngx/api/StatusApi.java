package yi.shi.plinth.ngx.api;

import com.google.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import yi.shi.plinth.annotation.auth.AUTH;
import yi.shi.plinth.annotation.http.HttpPath;
import yi.shi.plinth.annotation.http.HttpService;
import yi.shi.plinth.annotation.http.Method.GET;
import yi.shi.plinth.http.result.JSON;
import yi.shi.plinth.ngx.NginxProperties;
import yi.shi.plinth.ngx.dto.DiskInfo;
import yi.shi.plinth.ngx.dto.ProcInfo;
import yi.shi.plinth.ngx.dto.ServiceStatus;
import yi.shi.plinth.ngx.service.HostInfoService;
import yi.shi.plinth.ngx.service.NginxControlService;

import java.lang.ProcessHandle;

/**
 * 服务状态接口：nginx 进程/版本/运行时长 + 宿主机资源 + 面板 JVM。
 * 只读接口，不写审计日志（高频轮询）。
 */
@HttpService
public class StatusApi {

    @Inject
    private NginxControlService nginxControlService;

    @Inject
    private HostInfoService hostInfoService;

    @GET
    @HttpPath("/api/status")
    @AUTH(orRole = "admin")
    public JSON<ServiceStatus> status() {
        ServiceStatus status = new ServiceStatus();
        status.setNginx(nginxControlService.status());
        status.setFullVersion(nginxControlService.fullVersion());

        // ---- nginx master 运行时长 ----
        List<ProcInfo> processes = NginxProperties.isDockerMode()
                ? List.of() : hostInfoService.nginxProcesses();
        // 兜底：所有进程扫描可能漏掉服务形态的 master（如 Windows 下按其他会话运行），
        // 用 pid 文件探得的 PID 直接补一条 master 信息
        boolean hasMaster = processes.stream().anyMatch(ProcInfo::isMaster);
        Long pidFromFile = status.getNginx().getPid();
        if (!hasMaster && pidFromFile != null && pidFromFile > 0) {
            ProcInfo master = hostInfoService.masterByPid(pidFromFile);
            if (master != null) {
                processes.add(0, master);
            }
        }
        // 去重（同一 PID 可能因 command/name 匹配重复出现）
        status.setProcesses(processes.stream()
                .collect(java.util.stream.Collectors.toMap(ProcInfo::getPid, p -> p, (a, b) -> a,
                        java.util.LinkedHashMap::new))
                .values().stream().toList());
        status.setMasterUptime(masterUptime(processes, status.getNginx().getPid()));

        // ---- 宿主机 ----
        status.setOsName(hostInfoService.osName());
        status.setJvmVersion(hostInfoService.jvmVersion());
        status.setPanelUptimeSeconds((System.currentTimeMillis() - hostInfoService.panelStartEpoch()) / 1000);
        double cpu = hostInfoService.cpuLoad();
        status.setCpuLoad(cpu < 0 ? -1 : Math.round(cpu * 10.0) / 10.0);
        Long[] mem = hostInfoService.systemMemory();
        if (mem != null) {
            status.setMemUsed(mem[0]);
            status.setMemTotal(mem[1]);
        }
        Long[] heap = hostInfoService.jvmHeap();
        status.setJvmHeapUsed(heap[0]);
        status.setJvmHeapMax(heap[1]);
        List<DiskInfo> disks = hostInfoService.disks();
        status.setDisks(disks);
        return new JSON<>(status);
    }

    /** master 运行时长文本：优先匹配 master 进程，退化为 pid 文件探测到的 PID。 */
    private String masterUptime(List<ProcInfo> processes, Long pid) {
        Long masterPid = null;
        for (ProcInfo p : processes) {
            if (p.isMaster()) {
                masterPid = p.getPid();
                break;
            }
        }
        if (masterPid == null) {
            masterPid = pid;
        }
        if (masterPid == null || masterPid <= 0) {
            return null;
        }
        try {
            Instant started = ProcessHandle.of(masterPid)
                    .flatMap(p -> p.info().startInstant())
                    .orElse(null);
            if (started == null) {
                return null;
            }
            Duration d = Duration.between(started, Instant.now());
            return d.toDays() + "天 " + String.format("%02d:%02d:%02d",
                    d.toHoursPart(), d.toMinutesPart(), d.toSecondsPart());
        } catch (Exception e) {
            // 进程退出/权限不足时时长不可得，静默降级
            return null;
        }
    }
}
