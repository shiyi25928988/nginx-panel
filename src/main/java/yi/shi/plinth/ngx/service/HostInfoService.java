package yi.shi.plinth.ngx.service;

import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import yi.shi.plinth.App;
import yi.shi.plinth.ngx.dto.DiskInfo;
import yi.shi.plinth.ngx.dto.ProcInfo;

import java.lang.management.ManagementFactory;
import java.nio.file.FileStore;
import java.nio.file.FileSystems;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 宿主机与面板自身信息：CPU/内存、磁盘分区、面板 JVM、nginx 进程探测。
 *
 * <p>全部基于 JDK 自带能力（OperatingSystemMXBean / ProcessHandle / FileStore），
 * 无需依赖 shell 命令，跨平台可用。
 */
@Slf4j
@Singleton
public class HostInfoService {

    /** 系统 CPU 负载百分比（0-100，最近一个采样窗口；不可用返回 -1）。 */
    public double cpuLoad() {
        try {
            java.lang.management.OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
            if (os instanceof com.sun.management.OperatingSystemMXBean) {
                return ((com.sun.management.OperatingSystemMXBean) os).getCpuLoad() * 100;
            }
        } catch (Exception e) {
            log.debug("cpuLoad 读取失败: {}", e.getMessage());
        }
        return -1;
    }

    /** 系统内存用量。返回 [used, total]，不可用返回 null。 */
    public Long[] systemMemory() {
        try {
            java.lang.management.OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
            if (os instanceof com.sun.management.OperatingSystemMXBean) {
                com.sun.management.OperatingSystemMXBean cmos = (com.sun.management.OperatingSystemMXBean) os;
                long total = cmos.getTotalPhysicalMemorySize();
                long free = cmos.getFreePhysicalMemorySize();
                if (total > 0) {
                    return new Long[]{total - free, total};
                }
            }
        } catch (Exception e) {
            log.debug("systemMemory 读取失败: {}", e.getMessage());
        }
        return null;
    }

    /** 面板 JVM 堆用量。返回 [used, max]。 */
    public Long[] jvmHeap() {
        var pool = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        return new Long[]{pool.getUsed(), pool.getMax()};
    }

    /** 全部文件系统（磁盘分区）空间状态（按挂载点去重）。 */
    public List<DiskInfo> disks() {
        List<DiskInfo> disks = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (FileStore store : FileSystems.getDefault().getFileStores()) {
            if (!seen.add(store.toString())) {
                continue;
            }
            try {
                DiskInfo d = new DiskInfo();
                d.setMount(store.toString());
                d.setTotalBytes(store.getTotalSpace());
                d.setFreeBytes(store.getUsableSpace());
                d.setUsedPercent(d.getTotalBytes() > 0
                        ? Math.round((d.getTotalBytes() - d.getFreeBytes()) * 1000.0 / d.getTotalBytes()) / 10.0
                        : 0);
                disks.add(d);
            } catch (Exception e) {
                // 个别特殊分区不可读，跳过
                log.debug("跳过不可读分区 {}: {}", store, e.getMessage());
            }
        }
        return disks;
    }

    /** 面板启动 epoch 毫秒。 */
    public long panelStartEpoch() {
        return App.START_TIME;
    }

    /** 按 PID 直接取进程信息（兜底 master 探测），进程不存在返回 null。 */
    public ProcInfo masterByPid(long pid) {
        try {
            ProcessHandle h = ProcessHandle.of(pid).orElse(null);
            if (h == null) {
                return null;
            }
            String cmd = h.info().commandLine().orElseGet(() -> h.info().command().orElse("nginx"));
            ProcInfo info = new ProcInfo();
            info.setPid(pid);
            info.setCommand(cmd);
            info.setMaster(true);
            info.setStartedAt(h.info().startInstant().map(Instant::toEpochMilli).orElse(null));
            return info;
        } catch (Exception e) {
            return null;
        }
    }

    public String osName() {
        return System.getProperty("os.name") + " / " + System.getProperty("os.arch");
    }

    public String jvmVersion() {
        return "Java " + Runtime.version() + "（" + ManagementFactory.getRuntimeMXBean().getVmName() + "）";
    }

    /**
     * 探测 nginx 进程列表（native 模式）：master 命令行含 "master process"，
     * docker 容器内进程对宿主 ProcessHandle 通常不可见，返回空由前端提示。
     */
    public List<ProcInfo> nginxProcesses() {
        List<ProcInfo> list = new ArrayList<>();
        try {
            ProcessHandle.allProcesses().limit(200).forEach(p -> {
                try {
                    String cmd = p.info().commandLine().orElse("");
                    String name = p.info().command().orElse("");
                    if (name.endsWith("nginx") || name.contains("nginx")) {
                        ProcInfo info = new ProcInfo();
                        info.setPid(p.pid());
                        info.setCommand(cmd.isEmpty() ? name : cmd);
                        info.setMaster(cmd.contains("master process"));
                        info.setStartedAt(p.info().startInstant().map(Instant::toEpochMilli).orElse(null));
                        list.add(info);
                    }
                } catch (Exception ignored) {
                    // 进程可能瞬间退出
                }
            });
        } catch (Exception e) {
            log.debug("nginx 进程探测失败: {}", e.getMessage());
        }
        return list;
    }
}
