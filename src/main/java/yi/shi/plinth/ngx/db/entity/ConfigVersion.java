package yi.shi.plinth.ngx.db.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 配置文件版本：每次保存/回滚成功后写入一条，内容全文保存，支持追溯与回滚。
 */
@Data
public class ConfigVersion {
    private Long id;
    /** 相对 confDir 的文件路径 */
    private String filePath;
    /** 保存前的文件内容（新建文件时为 NULL） */
    private String oldContent;
    /** 保存后的文件内容 */
    private String newContent;
    /** newContent 的 MD5 */
    private String md5;
    /** 文件内递增版本号 */
    private Integer versionNo;
    private String editor;
    private String reason;
    private LocalDateTime createTime;
    /** 列表页专用标记：该版本是否基于已存在文件修改（true 表示可"撤销本版"） */
    private transient Boolean hasPrev;
}
