package yi.shi.plinth.ngx.api;

import com.google.inject.Inject;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import yi.shi.plinth.annotation.auth.AUTH;
import yi.shi.plinth.annotation.http.HttpParam;
import yi.shi.plinth.annotation.http.HttpPath;
import yi.shi.plinth.annotation.http.HttpService;
import yi.shi.plinth.annotation.http.Method.GET;
import yi.shi.plinth.http.result.JSON;
import yi.shi.plinth.ngx.db.entity.ConfigVersion;
import yi.shi.plinth.ngx.dto.PageData;
import yi.shi.plinth.ngx.service.VersionService;

/**
 * 版本历史接口：按文件分页追溯、查看某版本内容（前端做 diff）。
 */
@HttpService
public class HistoryApi {

    @Inject
    private VersionService versionService;

    @GET
    @HttpPath("/api/history")
    @AUTH(orRole = "admin")
    public JSON<PageData<ConfigVersion>> history(@HttpParam("path") String path,
                                                 @HttpParam("page") String page,
                                                 @HttpParam("size") String size) {
        return new JSON<>(versionService.listByPath(path, parseInt(page, 1), parseInt(size, 10)));
    }

    /** 取版本完整数据（前端渲染 diff 用）：content=修改后，prevContent=修改前（新建文件为 null）。 */
    @GET
    @HttpPath("/api/history/version")
    @AUTH(orRole = "admin")
    public JSON<Map<String, Object>> version(@HttpParam("id") String id) {
        long versionId = Long.parseLong(id);
        ConfigVersion meta = versionService.getMeta(versionId);
        Map<String, Object> result = new LinkedHashMap<>();
        if (meta != null) {
            result.put("id", meta.getId());
            result.put("filePath", meta.getFilePath());
            result.put("versionNo", meta.getVersionNo());
            result.put("md5", meta.getMd5());
            result.put("editor", meta.getEditor());
            result.put("reason", meta.getReason());
            result.put("createTime", meta.getCreateTime());
            // md5=DELETED 标记删除动作，此时 newContent 为空
            result.put("content", "DELETED".equals(meta.getMd5()) ? null : versionService.contentOf(versionId));
            result.put("prevContent", versionService.prevOf(versionId));
        }
        return new JSON<>(result);
    }

    /** 最近的改动（跨文件，控制台概览）。 */
    @GET
    @HttpPath("/api/version/recent")
    @AUTH(orRole = "admin")
    public JSON<List<ConfigVersion>> recent(@HttpParam("limit") String limit) {
        int n;
        try {
            n = Integer.parseInt(limit);
        } catch (Exception e) {
            n = 5;
        }
        return new JSON<>(versionService.recent(Math.max(1, Math.min(n, 50))));
    }

    private static int parseInt(String value, int defaultValue) {
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            return defaultValue;
        }
    }
}
