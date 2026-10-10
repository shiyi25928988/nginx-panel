package yi.shi.plinth.ngx.view;

import j2html.tags.ContainerTag;
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
import static j2html.TagCreator.main;
import static j2html.TagCreator.script;
import static j2html.TagCreator.span;
import static j2html.TagCreator.table;
import static j2html.TagCreator.tbody;
import static j2html.TagCreator.th;
import static j2html.TagCreator.thead;
import static j2html.TagCreator.tr;

/**
 * 控制台首页：nginx 运行状态、快捷操作、最近配置改动与最近操作日志。
 */
@HttpService
public class HomePage extends Page {

    @GET
    @HttpPath("/page/index")
    @AUTH(authUrl = "/page/login")
    public HTML index() throws Exception {
        HTML result = new HTML();
        result.setHtmlContent(createHtml().render());
        return result;
    }

    @GET
    @HttpPath("/")
    @AUTH(authUrl = "/page/login")
    public HTML root() throws Exception {
        return index();
    }

    @Override
    protected HeadTag createHead() throws Exception {
        return Head.createHead("控制台 - Nginx Shell");
    }

    @Override
    protected MainTag createMain() throws Exception {
        return main().with(
                div().withClass("container").with(
                        h5("控制台").withStyle("margin-top:20px;"),

                        // 状态卡片
                        div().withClass("card blue-grey darken-3").with(
                                div().withClass("card-content white-text").with(
                                        span("状态检测中…").withClass("card-title").withId("statusRunning"),
                                        div().withId("statusDetail").withClass("grey-text text-lighten-3")
                                )
                        ),

                        // 快捷操作 + 最近操作日志
                        div().withClass("row").with(
                                div().withClass("col s12 m6").with(
                                        div().withClass("card").with(
                                                div().withClass("card-content").with(
                                                        span("快捷操作").withClass("card-title"),
                                                        actionLink("校验配置 (nginx -t)", "blue", "btnTest", null),
                                                        actionLink("重载配置 (reload)", "orange", "btnReload", null),
                                                        actionLink("进入配置编辑", "grey darken-2", null, "/page/config")
                                                )
                                        )
                                ),
                                div().withClass("col s12 m6").with(
                                        div().withClass("card").with(
                                                div().withClass("card-content").with(
                                                        span("最近操作日志").withClass("card-title"),
                                                        table().withClass("highlight").with(
                                                                thead(tr(th("时间"), th("动作"), th("结果"))),
                                                                tbody().withId("recentLogsBody")
                                                        ),
                                                        a("全部日志 →").withHref("/page/audit").withStyle("font-size:0.85rem;")
                                                )
                                        )
                                )
                        ),

                        // 最近改动
                        div().withClass("row").with(
                                div().withClass("col s12").with(
                                        div().withClass("card").with(
                                                div().withClass("card-content").with(
                                                        span("最近配置改动").withClass("card-title"),
                                                        table().withClass("highlight").with(
                                                                thead(tr(th("时间"), th("文件"), th("版本"), th("操作人"), th("原因"))),
                                                                tbody().withId("recentVersionsBody")
                                                        ),
                                                        a("版本历史 →").withHref("/page/history").withStyle("font-size:0.85rem;")
                                                )
                                        )
                                )
                        )
                ),
                script().withSrc("/js/Console.js?v=" + yi.shi.plinth.App.START_TIME)
        );
    }

    /** Materialize 风格按钮（带 id 的用 <a id>，跳转的用 href）。 */
    private j2html.tags.specialized.ATag actionLink(String text, String color, String id, String href) {
        j2html.tags.specialized.ATag tag = a(text).withClass("btn waves-effect waves-light " + color)
                .withStyle("margin:4px;text-transform:none;");
        if (id != null) {
            return tag.withId(id).withHref("#");
        }
        return tag.withHref(href);
    }
}
