package yi.shi.plinth.ngx.view;

import j2html.tags.specialized.HeadTag;
import j2html.tags.specialized.MainTag;
import yi.shi.plinth.annotation.auth.AUTH;
import yi.shi.plinth.annotation.http.HttpPath;
import yi.shi.plinth.annotation.http.HttpService;
import yi.shi.plinth.annotation.http.Method.GET;
import yi.shi.plinth.http.result.HTML;
import yi.shi.plinth.view.Page;
import yi.shi.plinth.view.base.Head;

import static j2html.TagCreator.a;
import static j2html.TagCreator.div;
import static j2html.TagCreator.h5;
import static j2html.TagCreator.input;
import static j2html.TagCreator.label;
import static j2html.TagCreator.main;
import static j2html.TagCreator.script;
import static j2html.TagCreator.span;

/**
 * 网络拓扑页：解析当前配置链路（client→server→upstream/backend/backends），
 * 分层 SVG 渲染 + 悬停高亮 + 节点详情。
 */
@HttpService
public class TopologyPage extends Page {

    @GET
    @HttpPath("/page/topology")
    @AUTH(authUrl = "/page/login")
    public HTML topologyPage() throws Exception {
        HTML result = new HTML();
        result.setHtmlContent(createHtml().render());
        return result;
    }

    @Override
    protected HeadTag createHead() throws Exception {
        return Head.createHead("网络拓扑 - Nginx Shell").with(
                j2html.TagCreator.style().withText(
                        ".topo-legend{font-size:0.75rem;}"
                        + ".topo-svg .node rect{rx:6;}"
                        + ".topo-svg .node.client rect{fill:#263238;stroke:#90a4ae;}"
                        + ".topo-svg .node.server rect{fill:#1565c0;stroke:#64b5f6;}"
                        + ".topo-svg .node.upstream rect{fill:#6a1b9a;stroke:#ba68c8;}"
                        + ".topo-svg .node.backend rect{fill:#00695c;stroke:#4db6ac;}"
                        + ".topo-svg .node.static rect{fill:#5d4037;stroke:#a1887f;}"
                        + ".topo-svg .node text{fill:#fff;font-size:12px;}"
                        + ".topo-svg .node.dim rect,.topo-svg .node.dim text{opacity:0.25;}"
                        + ".topo-svg .node.sel rect{stroke:#ffd600;stroke-width:2.5px;}"
                        + ".topo-svg .edge{stroke:#b0bec5;stroke-width:1.6px;fill:none;}"
                        + ".topo-svg .edge.hot{stroke:#ffd600;stroke-width:2.6px;}"
                        + ".topo-svg .edge.dim{opacity:0.22;}"
                        + ".topo-svg .elabel{font-size:10px;fill:#78909c;}"
                        + ".topo-svg{background:#1e1e1e;border-radius:4px;display:block;cursor:pointer;}"
                        + ".topo-detail{font-size:0.8rem;max-height:260px;overflow:auto;}"
                        + ".topo-detail .chip{display:inline-block;padding:1px 8px;margin:2px 3px;background:#263238;"
                        + "color:#eceff1;border-radius:3px;font-family:monospace;}"
                )
        );
    }

    @Override
    protected MainTag createMain() throws Exception {
        return main().with(
                div().withClass("container").with(
                        h5("网络拓扑").withStyle("margin-top:20px;"),
                        span("由当前配置实时解析：Client → Server(监听/域名) → location 代理 → Upstream → Backend；root/alias 归为静态目录节点。配置保存+重载后自动更新。")
                                .withClass("grey-text").withStyle("font-size:0.8rem;display:block;"),
                        div().withClass("row").with(
                                div().withClass("col s12 m4").with(
                                        div().withClass("input-field").with(
                                                input().withId("topoFilter").withType("text"),
                                                label("筛选（域名/后端/路径，其余节点半透明）").withFor("topoFilter")
                                        )
                                ),
                                div().withClass("col s12 m8").with(
                                        div().withStyle("margin-top:16px;").with(
                                                a("刷新").withHref("#").withId("btnTopoRefresh")
                                                        .withClass("blue btn waves-effect").withStyle("margin:4px;text-transform:none;"),
                                                span().withId("topoHint").withClass("grey-text")
                                                        .withStyle("font-size:0.75rem;margin-left:8px;")
                                        )
                                )
                        ),
                        div().withClass("card").with(
                                div().withClass("card-content").with(
                                        div().withClass("topo-legend").with(
                                                span("● 监听/Server").withStyle("color:#64b5f6;"),
                                                span("● Upstream").withStyle("color:#ba68c8;margin-left:12px;"),
                                                span("● Backend").withStyle("color:#4db6ac;margin-left:12px;"),
                                                span("● 静态目录").withStyle("color:#a1887f;margin-left:12px;"),
                                                span("● 客户端").withStyle("color:#90a4ae;margin-left:12px;")
                                        ),
                                        div().withId("topoView").with(
                                                // JS 动态渲染 SVG
                                        )
                                )
                        ),
                        div().withClass("card").with(
                                div().withClass("card-content").with(
                                        span("节点详情").withClass("card-title").withStyle("font-size:1.1rem;"),
                                        span("点击拓扑中的节点查看引用配置文件/监听/域名/构成。").withClass("grey-text").withStyle("font-size:0.8rem;"),
                                        div().withId("topoDetail").withClass("topo-detail")
                                )
                        )
                ),
                script().withSrc("/js/Topology.js?v=" + yi.shi.plinth.App.START_TIME)
        );
    }
}
