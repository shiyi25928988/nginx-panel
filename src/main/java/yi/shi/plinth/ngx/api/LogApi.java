package yi.shi.plinth.ngx.api;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.List;
import yi.shi.plinth.annotation.auth.AUTH;
import yi.shi.plinth.annotation.http.HttpParam;
import yi.shi.plinth.annotation.http.HttpPath;
import yi.shi.plinth.annotation.http.HttpService;
import yi.shi.plinth.annotation.http.Method.GET;
import yi.shi.plinth.http.result.JSON;
import yi.shi.plinth.ngx.dto.LogFile;
import yi.shi.plinth.ngx.service.LogService;

/**
 * Nginx 日志接口：实时发现（配置指令 + 二进制默认）、安全 tail 读取。
 */
@HttpService
public class LogApi {

    @Inject
    private LogService logService;

    /** 当前配置声明的全部日志文件（读操作，不打审计）。 */
    @GET
    @HttpPath("/api/nglog/files")
    @AUTH(orRole = "admin")
    public JSON<List<LogFile>> files() {
        return new JSON<>(logService.discover());
    }

    /** 某日志文件尾部 N 行（结构化返回：裸字符串会被前端 JSON 解析判定失败）。 */
    @GET
    @HttpPath("/api/nglog/tail")
    @AUTH(orRole = "admin")
    public JSON<java.util.Map<String, Object>> tail(@HttpParam("path") String path,
                                                    @HttpParam("lines") String lines) {
        try {
            int n;
            try {
                n = Integer.parseInt(lines);
            } catch (Exception e) {
                n = 200;
            }
            java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
            result.put("path", path);
            result.put("content", logService.tail(path, n));
            return new JSON<>(result);
        } catch (IOException e) {
            throw new yi.shi.plinth.ngx.NginxPanelException("读取日志失败: " + e.getMessage(), e);
        }
    }
}
