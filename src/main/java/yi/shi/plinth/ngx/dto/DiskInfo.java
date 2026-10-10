package yi.shi.plinth.ngx.dto;

import lombok.Data;

/**
 * 文件系统（磁盘分区）状态。
 */
@Data
public class DiskInfo {
    /** 挂载点/盘符 */
    private String mount;
    private long totalBytes;
    private long freeBytes;
    /** 已用百分比（0-100） */
    private double usedPercent;
}
