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
import static j2html.TagCreator.option;
import static j2html.TagCreator.script;
import static j2html.TagCreator.select;
import static j2html.TagCreator.span;
import static j2html.TagCreator.table;
import static j2html.TagCreator.tbody;
import static j2html.TagCreator.th;
import static j2html.TagCreator.thead;
import static j2html.TagCreator.tr;

/**
 * 操作审计日志页：全部敏感操作（登录/保存/校验/重载/回滚等）留痕查询。
 */
@HttpService
public class AuditPage extends Page {

    @GET
    @HttpPath("/page/audit")
    @AUTH(authUrl = "/page/login")
    public HTML auditPage() throws Exception {
        HTML result = new HTML();
        result.setHtmlContent(createHtml().render());
        return result;
    }

    @Override
    protected HeadTag createHead() throws Exception {
        return Head.createHead("操作日志 - Nginx Shell");
    }

    @Override
    protected MainTag createMain() throws Exception {
        return main().with(
                div().withClass("container").with(
                        h5("操作日志").withStyle("margin-top:20px;"),
                        div().withClass("row").with(
                                div().withClass("col s12").with(
                                        div().withClass("card").with(
                                                div().withClass("card-content").with(
                                                        div().withClass("input-field").with(
                                                                select().withId("auditActionFilter").with(
                                                                        option("全部").withValue(""),
                                                                        option("登录").withValue("LOGIN"),
                                                                        option("保存配置").withValue("SAVE_CONFIG"),
                                                                        option("上传覆盖").withValue("UPLOAD_CONFIG"),
                                                                        option("回滚配置").withValue("ROLLBACK_CONFIG"),
                                                                        option("撤销版本").withValue("UNDO_VERSION"),
                                                                        option("应用模板").withValue("APPLY_TEMPLATE"),
                                                                        option("替换证书").withValue("REPLACE_CERT"),
                                                                        option("生成证书").withValue("GENERATE_CERT"),
                                                                        option("删除配置").withValue("DELETE_CONFIG"),
                                                                        option("校验(nginx -t)").withValue("TEST_CONFIG"),
                                                                        option("校验候选内容").withValue("VALIDATE_CONFIG"),
                                                                        option("重载(reload)").withValue("RELOAD"),
                                                                        option("修改密码").withValue("CHANGE_PASSWORD")
                                                                )
                                                        ),
                                                        table().withClass("highlight").with(
                                                                thead(tr(th("时间"), th("用户"), th("动作"), th("文件"), th("结果"), th("详情"))),
                                                                tbody().withId("auditTableBody")
                                                        ),
                                                        div().with(
                                                                a("上一页").withHref("#").withId("btnAuditPrev").withClass("btn-flat"),
                                                                span().withId("auditPageInfo"),
                                                                a("下一页").withHref("#").withId("btnAuditNext").withClass("btn-flat")
                                                        )
                                                )
                                        )
                                )
                        )
                ),
                script().withSrc("/js/Audit.js?v=" + yi.shi.plinth.App.START_TIME)
        );
    }
}
