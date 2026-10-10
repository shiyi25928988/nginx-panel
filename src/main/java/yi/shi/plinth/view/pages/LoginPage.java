package yi.shi.plinth.view.pages;

import j2html.tags.specialized.HtmlTag;
import yi.shi.plinth.annotation.http.HttpPath;
import yi.shi.plinth.annotation.http.HttpService;
import yi.shi.plinth.annotation.http.Method.GET;
import yi.shi.plinth.http.result.HTML;
import yi.shi.plinth.view.base.Head;
import yi.shi.plinth.view.element.Button;
import yi.shi.plinth.view.element.Input;

import static j2html.TagCreator.body;
import static j2html.TagCreator.div;
import static j2html.TagCreator.form;
import static j2html.TagCreator.h4;
import static j2html.TagCreator.html;
import static j2html.TagCreator.p;
import static j2html.TagCreator.script;
import static j2html.TagCreator.span;

/**
 * 登录页（单管理员，无注册入口）。
 */
@HttpService
public class LoginPage {

    @GET
    @HttpPath("/page/login")
    public HTML loginPage() throws Exception {
        HtmlTag htmlTag = html(
                Head.createHead("登录 - Nginx Shell"),
                body().withClass("blue-grey darken-3").with(
                        div().withClass("container").withStyle("margin-top:80px;").with(
                                div().withClass("center-align").withStyle("margin-bottom:24px;").with(
                                        h4("Nginx Shell").withClass("white-text").withStyle("font-weight:300;margin:0;letter-spacing:1px;"),
                                        p("Nginx 配置控制台").withClass("white-text").withStyle("opacity:0.85;margin-top:6px;font-size:0.9rem;")
                                ),
                                div().withClass("card").with(
                                        div().withClass("card-content").with(
                                                span("登录").withClass("card-title"),
                                                form().withId("loginForm").with(
                                                        Input.textInput("loginUsername", "用户名", "text"),
                                                        Input.textInput("loginPassword", "密码", "password"),
                                                        Button.create("登录", "blue", "loginBtn")
                                                )
                                        )
                                )
                        ),
                        // 登录页独立于 Page 基类，公共工具（apiError 等）在 Init.js 中，须先引入
                        script().withSrc("/js/Init.js?v=" + yi.shi.plinth.App.START_TIME),
                        script().withSrc("/js/Login.js?v=" + yi.shi.plinth.App.START_TIME)
                )
        );
        HTML result = new HTML();
        result.setHtmlContent(htmlTag.render());
        return result;
    }
}
