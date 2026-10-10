package yi.shi.plinth.ngx.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 通用分页结果。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PageData<T> {
    private List<T> list;
    private long total;
    private int page;
    private int size;
}
