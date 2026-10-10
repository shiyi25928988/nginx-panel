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
import static j2html.TagCreator.main;
import static j2html.TagCreator.script;
import static j2html.TagCreator.span;

/**
 * 内嵌配置模板页：主配置/静态站/反向代理/负载均衡/SSL 等，填变量一键生成配置文件。
 */
@HttpService
public class TemplatesPage extends Page {

    @GET
    @HttpPath("/page/templates")
    @AUTH(authUrl = "/page/login")
    public HTML templatesPage() throws Exception {
        HTML result = new HTML();
        result.setHtmlContent(createHtml().render());
        return result;
    }

    @Override
    protected HeadTag createHead() throws Exception {
        return Head.createHead("配置模板 - Nginx Shell");
    }

    @Override
    protected MainTag createMain() throws Exception {
        return main().with(
                div().withClass("container").with(
                        h5("配置模板").withStyle("margin-top:20px;"),
                        div().withClass("row").with(
                                div().withClass("col s12").with(
                                        span("模板内置 {{变量}} 占位符，应用时自动替换；生成后仍会执行 nginx -t 校验，失败自动撤销。")
                                                .withClass("grey-text")
                                )
                        ),
                        div().withClass("row").withId("templateCards"),
                        // 应用模板弹层
                        div().withId("applyModal").withClass("modal modal-fixed-footer").with(
                                div().withClass("modal-content").with(
                                        h5().withId("applyTitle"),
                                        div().withClass("input-field").with(
                                                j2html.TagCreator.input().withId("applyPath").withType("text"),
                                                j2html.TagCreator.label("保存为（conf.d/xxx.conf）").withFor("applyPath")
                                        ),
                                        div().withId("applyVars"),
                                        div().withClass("input-field").with(
                                                j2html.TagCreator.input().withId("applyReason").withType("text"),
                                                j2html.TagCreator.label("修改原因").withFor("applyReason")
                                        ),
                                        div().with(
                                                span("预览").withStyle("display:block;margin-top:12px;"),
                                                div().withId("applyPreview")
                                                        .withStyle("border:1px solid #e0e0e0;padding:8px;background:#f6f6f6;"
                                                                + "font-family:monospace;font-size:0.8rem;white-space:pre-wrap;max-height:260px;overflow:auto;")
                                        )
                                ),
                                div().withClass("modal-footer").with(
                                        a("生成并校验").withHref("#").withId("btnApplyConfirm").withClass("waves-effect blue btn"),
                                        a("取消").withHref("#").withClass("modal-close waves-effect btn-flat")
                                )
                        )
                ),
                script().withSrc("/js/Templates.js?v=" + yi.shi.plinth.App.START_TIME)
        );
    }
}
