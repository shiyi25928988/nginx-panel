package yi.shi.plinth.ngx.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 配置目录树节点。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class FileNode {

    /** 相对 confDir 的路径（用 / 分隔），目录以 / 结尾 */
    private String path;
    /** 显示名 */
    private String name;
    /** 是否目录 */
    private boolean directory;
    /** 是否可在线编辑保存（.conf / .types / mime.types） */
    private boolean editable;
    /** 字节数（目录为 0） */
    private long size;
    /** 最后修改时间（epoch millis） */
    private Long lastModified;
}
