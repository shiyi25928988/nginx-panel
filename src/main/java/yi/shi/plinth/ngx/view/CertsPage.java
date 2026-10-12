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
import static j2html.TagCreator.table;
import static j2html.TagCreator.tbody;
import static j2html.TagCreator.th;
import static j2html.TagCreator.thead;
import static j2html.TagCreator.tr;

/**
 * 证书管理页：清单（到期预警/域名/配对私钥）+ 替换上传 + 自签生成。
 */
@HttpService
public class CertsPage extends Page {

    @GET
    @HttpPath("/page/certs")
    @AUTH(authUrl = "/page/login")
    public HTML certsPage() throws Exception {
        HTML result = new HTML();
        result.setHtmlContent(createHtml().render());
        return result;
    }

    @Override
    protected HeadTag createHead() throws Exception {
        return Head.createHead("证书管理 - Nginx Shell");
    }

    @Override
    protected MainTag createMain() throws Exception {
        return main().with(
                div().withClass("container").with(
                        h5("证书管理").withStyle("margin-top:20px;"),
                        span("清单 = 配置引用的证书 + 证书目录（cert.dir，默认 /app/certs）全部证书（含自签未引用的）；替换走校验流水线（nginx -t + 版本留痕），失败自动还原。"
                                + "上传/生成的证书统一存放在面板证书目录（cert.dir，默认 /app/certs），"
                                + "/etc/ssl 等系统证书不参与面板替换，请修改配置 ssl_certificate 指向面板证书目录。")
                                .withClass("grey-text").withStyle("font-size:0.8rem;display:block;"),

                        // 清单
                        div().withClass("row").with(
                                div().withClass("col s12").with(
                                        div().withClass("card").with(
                                                div().withClass("card-content").with(
                                                        span("证书清单").withClass("card-title").withStyle("font-size:1.1rem;"),
                                                        div().with(
                                                                a("刷新").withHref("#").withId("btnCertRefresh")
                                                                        .withClass("blue btn waves-effect").withStyle("margin:4px;text-transform:none;"),
                                                                a("替换证书").withHref("#").withId("btnCertReplace")
                                                                        .withClass("indigo btn waves-effect").withStyle("margin:4px;text-transform:none;")
                                                        ),
                                                        table().withClass("highlight").with(
                                                                thead(tr(th("状态"), th("域名"), th("签发者"), th("有效期至"),
                                                                        th("剩余天数"), th("证书路径"), th("私钥"), th("算法"), th("操作"))),
                                                                tbody().withId("certTableBody")
                                                        )
                                                )
                                        )
                                )
                        ),

                        // 自签生成
                        div().withClass("row").with(
                                div().withClass("col s12 m8").with(
                                        div().withClass("card").with(
                                                div().withClass("card-content").with(
                                                        span("自签证书生成（内部域名/测试）").withClass("card-title").withStyle("font-size:1.1rem;"),
                                                        div().withClass("row").with(
                                                                div().withClass("input-field col s12 m3").with(
                                                                        input().withId("genName").withType("text"),
                                                                        label("文件名").withFor("genName")
                                                                ),
                                                                div().withClass("input-field col s12 m3").with(
                                                                        input().withId("genCn").withType("text"),
                                                                        label("CN").withFor("genCn")
                                                                ),
                                                                div().withClass("input-field col s12 m4").with(
                                                                        input().withId("genSans").withType("text"),
                                                                        label("SAN（逗号分隔）").withFor("genSans")
                                                                ),
                                                                div().withClass("input-field col s12 m2").with(
                                                                        input().withId("genDays").withType("number"),
                                                                        label("有效天数").withFor("genDays")
                                                                )
                                                        ),
                                                        div().withClass("input-field").with(
                                                                input().withId("genPassword").withType("password"),
                                                                label("管理员密码（仅当同名文件已存在时必填，用于确认覆盖）").withFor("genPassword")
                                                        ),
                                                        a("生成").withHref("#").withId("btnGenCert")
                                                                .withClass("teal btn waves-effect").withStyle("margin:4px;text-transform:none;")
                                                )
                                        )
                                )
                        ),

                        // 替换证书弹层
                        div().withId("certModal").withClass("modal modal-fixed-footer").with(
                                div().withClass("modal-content").with(
                                        h5("替换证书"),
                                        div().withClass("input-field").with(
                                                j2html.TagCreator.select().withId("certPathSelect")
                                                        .withStyle("display:block;")
                                        ),
                                        span("目标证书文件").withClass("grey-text").withStyle("font-size:0.8rem;"),
                                        div().withClass("row").with(
                                                div().withClass("file-field input-field col s12").with(
                                                        div().withClass("btn teal").with(
                                                                span("选择证书 .crt/.pem"),
                                                                input().withType("file").withId("certFileInput")
                                                        ),
                                                        div().withClass("file-path-wrapper").with(
                                                                input().withClass("file-path validate").withType("text")
                                                        )
                                                ),
                                                div().withClass("file-field input-field col s12").with(
                                                        div().withClass("btn blue-grey").with(
                                                                span("选择私钥 .key/.pem（可留空）"),
                                                                input().withType("file").withId("keyFileInput")
                                                        ),
                                                        div().withClass("file-path-wrapper").with(
                                                                input().withClass("file-path validate").withType("text")
                                                        )
                                                )
                                        ),
                                        div().withClass("input-field").with(
                                                input().withId("certReason").withType("text"),
                                                label("修改原因").withFor("certReason")
                                        ),
                                        div().with(
                                                input().withType("checkbox").withId("certAutoReload").withClass("filled-in"),
                                                label("成功后自动重载").withFor("certAutoReload")
                                        ),
                                        div().withClass("input-field").with(
                                                input().withId("certPassword").withType("password"),
                                                label("管理员密码（二次确认）").withFor("certPassword")
                                        )
                                ),
                                div().withClass("modal-footer").with(
                                        a("替换").withHref("#").withId("btnCertConfirm").withClass("waves-effect indigo btn"),
                                        a("取消").withHref("#").withClass("modal-close waves-effect btn-flat")
                                )
                        )
                ),
                script().withSrc("/js/Certs.js?v=" + yi.shi.plinth.App.START_TIME)
        );
    }
}
