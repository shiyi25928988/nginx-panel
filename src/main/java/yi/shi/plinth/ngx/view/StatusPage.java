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

import static j2html.TagCreator.div;
import static j2html.TagCreator.h5;
import static j2html.TagCreator.main;
import static j2html.TagCreator.script;
import static j2html.TagCreator.span;
import static j2html.TagCreator.table;
import static j2html.TagCreator.tbody;
import static j2html.TagCreator.th;
import static j2html.TagCreator.thead;
import static j2html.TagCreator.tr;

/**
 * 服务状态面板：nginx 进程详情、完整版本/编译参数、master 运行时长，
 * 以及宿主机 CPU/内存/磁盘和面板 JVM 状态，每 15 秒自动刷新。
 */
@HttpService
public class StatusPage extends Page {

    @GET
    @HttpPath("/page/status")
    @AUTH(authUrl = "/page/login")
    public HTML statusPage() throws Exception {
        HTML result = new HTML();
        result.setHtmlContent(createHtml().render());
        return result;
    }

    @Override
    protected HeadTag createHead() throws Exception {
        return Head.createHead("服务状态 - Nginx Shell");
    }

    @Override
    protected MainTag createMain() throws Exception {
        return main().with(
                div().withClass("container").with(
                        div().with(
                                h5("服务状态").withStyle("margin-top:20px;display:inline-block;"),
                                span("").withClass("grey-text").withId("statusRefreshHint")
                                        .withStyle("font-size:0.8rem;margin-left:12px;")
                        ),

                        div().withClass("row").with(
                                // Nginx 状态
                                div().withClass("col s12 l6").with(
                                        div().withClass("card blue-grey darken-3").with(
                                                div().withClass("card-content white-text").with(
                                                        span().withId("nginxRunning").withText("状态检测中…").withClass("card-title"),
                                                        table().withClass("highlight white-text").with(
                                                                tbody().withId("nginxStatusBody")
                                                        )
                                                )
                                        ),
                                        div().withClass("card blue-grey darken-3").with(
                                                div().withClass("card-content white-text").with(
                                                        span("Nginx 完整版本（nginx -V）").withClass("card-title")
                                                                .withStyle("font-size:1.1rem;"),
                                                        div().withId("nginxFullVersion")
                                                                .withStyle("font-family:monospace;font-size:0.8rem;white-space:pre-wrap;")
                                                )
                                        )
                                ),
                                // 宿主机
                                div().withClass("col s12 l6").with(
                                        div().withClass("card").with(
                                                div().withClass("card-content").with(
                                                        span("宿主机资源").withClass("card-title").withStyle("font-size:1.1rem;"),
                                                        div().withStyle("margin:12px 0;").with(
                                                                span("CPU 负载").withStyle("display:block;"),
                                                                div().withClass("progress").with(
                                                                        div().withClass("determinate").withId("cpuBar").withStyle("width:0%;")
                                                                ),
                                                                span().withId("cpuText").withClass("grey-text").withStyle("font-size:0.8rem;")
                                                        ),
                                                        div().withStyle("margin:12px 0;").with(
                                                                span("内存使用").withStyle("display:block;"),
                                                                div().withClass("progress").with(
                                                                        div().withClass("determinate purple").withId("memBar").withStyle("width:0%;")
                                                                ),
                                                                span().withId("memText").withClass("grey-text").withStyle("font-size:0.8rem;")
                                                        ),
                                                        div().withId("hostDetail").withClass("grey-text").withStyle("font-size:0.85rem;")
                                                )
                                        ),
                                        div().withClass("card").with(
                                                div().withClass("card-content").with(
                                                        span("磁盘空间").withClass("card-title").withStyle("font-size:1.1rem;"),
                                                        div().withId("diskList")
                                                )
                                        ),
                                        div().withClass("card").with(
                                                div().withClass("card-content").with(
                                                        span("Nginx Shell 面板").withClass("card-title").withStyle("font-size:1.1rem;"),
                                                        div().withId("panelDetail").withClass("grey-text").withStyle("font-size:0.85rem;")
                                                )
                                        )
                                )
                        )
                ),
                script().withSrc("/js/Status.js?v=" + yi.shi.plinth.App.START_TIME)
        );
    }
}
