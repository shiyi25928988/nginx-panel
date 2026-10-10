package yi.shi.plinth.ngx.api;

import com.google.inject.Inject;
import java.util.List;
import yi.shi.plinth.annotation.auth.AUTH;
import yi.shi.plinth.annotation.http.HttpBody;
import yi.shi.plinth.annotation.http.HttpParam;
import yi.shi.plinth.annotation.http.HttpPath;
import yi.shi.plinth.annotation.http.HttpService;
import yi.shi.plinth.annotation.http.Method.GET;
import yi.shi.plinth.annotation.http.Method.POST;
import yi.shi.plinth.http.result.JSON;
import yi.shi.plinth.ngx.dto.ApplyTemplateRequest;
import yi.shi.plinth.ngx.dto.FileContent;
import yi.shi.plinth.ngx.dto.FileNode;
import yi.shi.plinth.ngx.dto.SaveConfigRequest;
import yi.shi.plinth.ngx.dto.SaveResult;
import yi.shi.plinth.ngx.dto.UploadConfigRequest;
import yi.shi.plinth.ngx.service.ConfigFileService;

/**
 * 配置文件接口：目录 / 读取 / 保存 / 删除 / 回滚 / 应用模板。
 */
@HttpService
public class ConfigApi {

    @Inject
    private ConfigFileService configFileService;

    @GET
    @HttpPath("/api/files")
    @AUTH(orRole = "admin")
    public JSON<List<FileNode>> files() {
        return new JSON<>(configFileService.listFiles());
    }

    @GET
    @HttpPath("/api/file")
    @AUTH(orRole = "admin")
    public JSON<FileContent> file(@HttpParam("path") String path) {
        return new JSON<>(configFileService.read(path));
    }

    @POST
    @HttpPath("/api/file/save")
    @AUTH(orRole = "admin")
    public JSON<SaveResult> save(@HttpBody SaveConfigRequest req) {
        return new JSON<>(configFileService.save(req));
    }

    @POST
    @HttpPath("/api/file/delete")
    @AUTH(orRole = "admin")
    public JSON<String> delete(@HttpParam("path") String path) {
        configFileService.delete(path);
        return new JSON<>("deleted");
    }

    /** 上传文件整体覆盖/新建（需管理员密码二次确认，走校验流水线）。 */
    @POST
    @HttpPath("/api/file/upload")
    @AUTH(orRole = "admin")
    public JSON<SaveResult> upload(@HttpBody UploadConfigRequest req) {
        return new JSON<>(configFileService.upload(
                req.getPath(), req.getContent(), req.getPassword(), req.getReason(), req.getReloadAfter()));
    }

    /**
     * 在线校验候选内容（不落盘）：任意可编辑文件。
     * content 为 null 时校验该文件磁盘当前版本；否则校验编辑器未保存内容
     * （临时替换目标文件 → 整树 nginx -t → finally 还原，目标文件必须可编辑）。
     */
    @POST
    @HttpPath("/api/file/validate")
    @AUTH(orRole = "admin")
    public JSON<SaveResult> validate(@HttpBody UploadConfigRequest req) {
        return new JSON<>(configFileService.validateCandidate(req.getPath(), req.getContent()));
    }

    @POST
    @HttpPath("/api/template/apply")
    @AUTH(orRole = "admin")
    public JSON<SaveResult> applyTemplate(@HttpBody ApplyTemplateRequest req) {
        return new JSON<>(configFileService.applyTemplate(
                req.getTemplate(), req.getPath(), req.getVars(), req.getReason(), req.getReloadAfter()));
    }
}
