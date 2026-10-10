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
import yi.shi.plinth.ngx.dto.CertView;
import yi.shi.plinth.ngx.dto.GenerateCertRequest;
import yi.shi.plinth.ngx.dto.ReplaceCertRequest;
import yi.shi.plinth.ngx.dto.SaveResult;
import yi.shi.plinth.ngx.service.CertService;

/**
 * 证书管理接口：清单发现、替换（密码确认 + nginx -t 流水线）、自签生成。
 */
@HttpService
public class CertApi {

    @Inject
    private CertService certService;

    @GET
    @HttpPath("/api/certs")
    @AUTH(orRole = "admin")
    public JSON<List<CertView>> certs() {
        return new JSON<>(certService.list());
    }

    @POST
    @HttpPath("/api/certs/replace")
    @AUTH(orRole = "admin")
    public JSON<SaveResult> replace(@HttpBody ReplaceCertRequest req) {
        return new JSON<>(certService.replace(
                req.getCertPath(), req.getCertPem(), req.getKeyPem(),
                req.getPassword(), req.getReason(), req.getReloadAfter()));
    }

    /** 删除证书（cert.dir 内的上传/自签证书，连配对私钥；被配置引用的拒绝删除）。 */
    @POST
    @HttpPath("/api/certs/delete")
    @AUTH(orRole = "admin")
    public JSON<SaveResult> delete(@HttpParam("path") String path) {
        return new JSON<>(certService.delete(path));
    }

    @POST
    @HttpPath("/api/certs/generate")
    @AUTH(orRole = "admin")
    public JSON<SaveResult> generate(@HttpBody GenerateCertRequest req) {
        return new JSON<>(certService.generate(
                req.getName(), req.getCn(), req.getSans(), req.getDays(),
                req.getPassword(), req.getReason()));
    }
}
