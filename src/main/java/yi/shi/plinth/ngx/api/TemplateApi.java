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
import yi.shi.plinth.ngx.service.TemplateService;
import yi.shi.plinth.ngx.service.TemplateService.TemplateMeta;

/**
 * 配置模板接口：模板列表 / 原文与占位符变量。
 */
@HttpService
public class TemplateApi {

    @Inject
    private TemplateService templateService;

    @GET
    @HttpPath("/api/templates")
    @AUTH(orRole = "admin")
    public JSON<List<TemplateMeta>> templates() {
        return new JSON<>(templateService.list());
    }

    /** 模板原文 + 占位符变量名（前端据此动态生成表单）。 */
    @GET
    @HttpPath("/api/template")
    @AUTH(orRole = "admin")
    public JSON<Map<String, Object>> detail(@HttpParam("name") String name) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", name);
        result.put("raw", templateService.raw(name));
        result.put("variables", templateService.variables(name));
        return new JSON<>(result);
    }
}
