package yi.shi.plinth.ngx.api;

import com.google.inject.Inject;
import yi.shi.plinth.annotation.auth.AUTH;
import yi.shi.plinth.annotation.http.HttpPath;
import yi.shi.plinth.annotation.http.HttpService;
import yi.shi.plinth.annotation.http.Method.GET;
import yi.shi.plinth.http.result.JSON;
import yi.shi.plinth.ngx.dto.TopologyGraph;
import yi.shi.plinth.ngx.service.TopologyService;

/**
 * 网络拓扑接口：解析当前配置给出 server→upstream/backend 的链路数据。
 */
@HttpService
public class TopologyApi {

    @Inject
    private TopologyService topologyService;

    @GET
    @HttpPath("/api/topology")
    @AUTH(orRole = "admin")
    public JSON<TopologyGraph> topology() {
        return new JSON<>(topologyService.analyze());
    }
}
