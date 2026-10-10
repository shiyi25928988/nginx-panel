package yi.shi.plinth.ngx.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 配置文件内容响应。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class FileContent {
    private String path;
    private String content;
    private boolean editable;
    private long size;
    private Long lastModified;
    /** 当前版本号（无历史为 0） */
    private int versionCount;
}
