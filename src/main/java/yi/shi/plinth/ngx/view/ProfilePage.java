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
import yi.shi.plinth.view.element.Button;
import yi.shi.plinth.view.element.Input;

import static j2html.TagCreator.h5;
import static j2html.TagCreator.main;
import static j2html.TagCreator.form;
import static j2html.TagCreator.script;
import static j2html.TagCreator.span;
import static j2html.TagCreator.div;

/**
 * 个人资料页：当前管理员信息 + 修改密码。
 */
@HttpService
public class ProfilePage extends Page {

    @GET
    @HttpPath("/page/profile")
    @AUTH(authUrl = "/page/login")
    public HTML profilePage() throws Exception {
        HTML result = new HTML();
        result.setHtmlContent(createHtml().render());
        return result;
    }

    @Override
    protected HeadTag createHead() throws Exception {
        return Head.createHead("修改密码 - Nginx Shell");
    }

    @Override
    protected MainTag createMain() throws Exception {
        return main().with(
                div().withClass("container").with(
                        h5("修改密码").withStyle("margin-top:20px;"),
                        div().withClass("row").with(
                                div().withClass("col s12 m6").with(
                                        div().withClass("card").with(
                                                div().withClass("card-content").with(
                                                        span().withId("profileTitle").withText("当前管理员").withClass("card-title"),
                                                        div().withId("profileDetail").withClass("grey-text")
                                                )
                                        )
                                ),
                                div().withClass("col s12 m6").with(
                                        div().withClass("card").with(
                                                div().withClass("card-content").with(
                                                        span("修改密码").withClass("card-title"),
                                                        form().withId("passwordForm").with(
                                                                Input.textInput("oldPassword", "旧密码", "password"),
                                                                Input.textInput("newPassword", "新密码（≥8 位）", "password"),
                                                                Input.textInput("newPassword2", "确认新密码", "password"),
                                                                Button.create("修改密码", "blue", "btnChangePassword")
                                                        )
                                                )
                                        )
                                )
                        )
                ),
                script().withSrc("/js/Profile.js?v=" + yi.shi.plinth.App.START_TIME)
        );
    }
}
