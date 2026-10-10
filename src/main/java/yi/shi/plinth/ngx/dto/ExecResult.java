package yi.shi.plinth.ngx.dto;

import lombok.Data;

/**
 * 外部命令执行结果。
 */
@Data
public class ExecResult {
    /** 原始命令行 */
    private String command;
    private int exitCode;
    private boolean timeout;
    /** 合并后的 stdout+stderr */
    private String output;

    public boolean isSuccess() {
        return !timeout && exitCode == 0;
    }
}
