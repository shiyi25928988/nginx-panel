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
import static j2html.TagCreator.option;
import static j2html.TagCreator.script;
import static j2html.TagCreator.select;
import static j2html.TagCreator.span;

/**
 * Nginx 日志面板：自动发现 error.log/access.log（配置指令 + 二进制默认路径），
 * 支持尾部行数选择、关键字过滤、自动刷新与滚动到底。
 */
@HttpService
public class LogsPage extends Page {

    @GET
    @HttpPath("/page/logs")
    @AUTH(authUrl = "/page/login")
    public HTML logsPage() throws Exception {
        HTML result = new HTML();
        result.setHtmlContent(createHtml().render());
        return result;
    }

    @Override
    protected HeadTag createHead() throws Exception {
        return Head.createHead("Nginx 日志 - Nginx Shell");
    }

    @Override
    protected MainTag createMain() throws Exception {
        return main().with(
                div().withClass("container").with(
                        h5("Nginx 日志").withStyle("margin-top:20px;"),
                        div().withClass("card").with(
                                div().withClass("card-content").with(
                                        span("日志文件由当前配置实时发现（error_log / access_log 指令 + nginx 编译默认路径），改配置重载后自动跟随")
                                                .withClass("grey-text").withStyle("font-size:0.8rem;display:block;"),
                                        div().withClass("row").with(
                                                div().withClass("input-field col s12 m5 l4").with(
                                                        select().withId("logFileSelect"),
                                                        label("日志文件").withFor("logFileSelect")
                                                ),
                                                div().withClass("input-field col s6 m2 l1").with(
                                                        select().withId("tailLines").with(
                                                                option("100 行").withValue("100"),
                                                                option("500 行").withValue("500").attr("selected", "selected"),
                                                                option("2000 行").withValue("2000"),
                                                                option("5000 行").withValue("5000")
                                                        ),
                                                        label("尾部行数").withFor("tailLines")
                                                ),
                                                div().withClass("input-field col s12 m5 l4").with(
                                                        input().withId("logFilter").withType("text"),
                                                        label("关键字过滤（区分大小写，前端过滤）").withFor("logFilter")
                                                ),
                                                div().withClass("col s12 m6 l3").with(
                                                        div().withStyle("margin-top:16px;").with(
                                                                input().withType("checkbox").withId("logAutoRefresh").withClass("filled-in"),
                                                                label("自动刷新(10s)").withFor("logAutoRefresh")
                                                        )
                                                )
                                        ),
                                        div().with(
                                                refreshBtn("刷新", "blue", "btnLogRefresh"),
                                                refreshBtn("跟随滚动", "grey darken-1", "btnAutoScroll"),
                                                span("").withId("logFetchHint").withClass("grey-text")
                                                        .withStyle("font-size:0.75rem;margin-left:8px;")
                                        )
                                )
                        ),
                        div().withClass("card").with(
                                div().withClass("card-content").with(
                                        div().withId("logView")
                                                .withStyle("background:#1e1e1e;color:#d4d4d4;font-family:monospace;"
                                                        + "font-size:0.78rem;white-space:pre;overflow:auto;"
                                                        + "max-height:70vh;padding:10px;")
                                )
                        )
                ),
                script().withSrc("/js/Logs.js?v=" + yi.shi.plinth.App.START_TIME)
        );
    }

    private j2html.tags.specialized.ATag refreshBtn(String text, String color, String id) {
        return a(text).withClass("btn waves-effect waves-light " + color).withId(id)
                .withHref("#").withStyle("margin:4px;text-transform:none;");
    }
}
