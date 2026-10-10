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
import static j2html.TagCreator.select;
import static j2html.TagCreator.span;

/**
 * 配置文件编辑页：左侧文件列表，右侧 CodeMirror 编辑器 + 保存/校验/删除，
 * 保存自动 nginx -t，可勾选"保存后自动重载"。
 */
@HttpService
public class ConfigPage extends Page {

    @GET
    @HttpPath("/page/config")
    @AUTH(authUrl = "/page/login")
    public HTML configPage() throws Exception {
        HTML result = new HTML();
        result.setHtmlContent(createHtml().render());
        return result;
    }

    @Override
    protected HeadTag createHead() throws Exception {
        return Head.createHead("配置文件 - Nginx Shell").with(
                j2html.TagCreator.link().withRel("stylesheet").withHref("/vendor/codemirror/codemirror.css"),
                j2html.TagCreator.link().withRel("stylesheet").withHref("/vendor/codemirror/material-darker.css"),
                // 编辑器高度 + 全屏放大样式
                j2html.TagCreator.style().withText(
                        "#editor .CodeMirror{height:480px;}"
                        + ".editor-fullscreen{position:fixed;top:0;left:0;right:0;bottom:0;z-index:9000;"
                        + "margin:0 !important;border-radius:0 !important;overflow:auto;}"
                        + ".editor-fullscreen .card-content{height:100%;display:flex;flex-direction:column;}"
                        + ".editor-fullscreen #editor .CodeMirror{height:100% !important;flex:1;}"
                        + ".editor-fullscreen .hide-on-fullscreen{display:none !important;}"
                )
        );
    }

    @Override
    protected MainTag createMain() throws Exception {
        return main().with(
                div().withClass("container").with(
                        h5("配置文件").withStyle("margin-top:20px;"),
                        div().withClass("row").with(
                                // 左侧：文件列表
                                div().withClass("col s12 m4 l3").with(
                                        div().withClass("card").with(
                                                div().withClass("card-content").with(
                                                        span("配置目录文件").withClass("card-title").withStyle("font-size:1.1rem;"),
                                                        select().withId("fileSelect").withStyle("display:block;max-height:400px;overflow:auto;white-space:pre-wrap;"),
                                                        div().withClass("divider").withStyle("margin:12px 0;"),
                                                        a("+ 新建文件").withHref("#").withId("btnNewFile")
                                                                .withClass("teal btn waves-effect").withStyle("margin:4px;text-transform:none;width:100%;")
                                                )
                                        )
                                ),
                                // 右侧：编辑器
                                div().withClass("col s12 m8 l9").with(
                                        div().withClass("card").with(
                                                div().withClass("card-content").with(
                                                        span().withId("editorTitle").withText("未选择文件").withClass("card-title").withStyle("font-size:1.1rem;"),
                                                        div().with(
                                                                input().withType("checkbox").withId("autoReload").withClass("filled-in"),
                                                                label("保存后自动重载配置").withFor("autoReload").withStyle("margin-right:16px;")
                                                        ),
                                                        div().withClass("input-field").with(
                                                                input().withId("saveReason").withType("text"),
                                                                label("修改原因（记录到版本历史与日志）").withFor("saveReason")
                                                        ),
                                                        div().withClass("editorToolbar").with(
                                                                actionBtn("保存并校验", "blue", "btnSaveFile"),
                                                                actionBtn("校验当前内容", "teal", "btnValidateMain"),
                                                                actionBtn("nginx -t 校验", "cyan darken-1", "btnTestOnly"),
                                                                actionBtn("nginx -s 重载", "orange", "btnReload"),
                                                                actionBtn("上传替换", "indigo", "btnUploadOpen"),
                                                                actionBtn("查看历史", "grey darken-1", "btnHistory"),
                                                                actionBtn("删除文件", "red", "btnDelete"),
                                                                actionBtn("放大", "dark", "btnEditorZoom")
                                                        ),
                                                        div().withStyle("border:1px solid #e0e0e0;").with(
                                                                div().withId("editor").withStyle("display:none;"),
                                                                textareaFallback()
                                                        ),
                                                        div().withId("outputPanel").withClass("card-panel grey darken-4 grey-text text-lighten-3")
                                                                .withStyle("display:none;max-height:300px;overflow:auto;font-family:monospace;white-space:pre-wrap;margin-top:12px;")
                                                )
                                        )
                                )
                        )
                ),
                // 上传替换弹层：整体覆盖/新建 nginx 配置文件，需管理员密码二次确认
                div().withId("uploadModal").withClass("modal modal-fixed-footer").with(
                        div().withClass("modal-content").with(
                                h5("上传替换配置文件"),
                                j2html.TagCreator.p("上传内容会写入目标文件并执行 nginx -t，失败自动回滚；覆盖前需输入管理员密码确认，且全程留痕（版本 + 审计日志）。")
                                        .withClass("indigo-text").withStyle("font-size:0.85rem;"),
                                div().withClass("row").with(
                                        div().withClass("file-field input-field col s12").with(
                                                div().withClass("btn teal").with(
                                                        span("选择本地文件"),
                                                        input().withType("file").withId("uploadFileInput")
                                                ),
                                                div().withClass("file-path-wrapper").with(
                                                        input().withClass("file-path validate").withType("text")
                                                )
                                        ),
                                        div().withClass("input-field col s12").with(
                                                input().withId("uploadPath").withType("text"),
                                                label("目标文件（相对配置目录，如 nginx.conf）").withFor("uploadPath")
                                        ),
                                        div().withClass("input-field col s12").with(
                                                input().withId("uploadReason").withType("text"),
                                                label("修改原因").withFor("uploadReason")
                                        ),
                                        div().withClass("input-field col s12").with(
                                                input().withId("uploadPassword").withType("password"),
                                                label("管理员密码（二次确认）").withFor("uploadPassword")
                                        )
                                ),
                                span("内容预览（前 60 行）").withStyle("display:block;margin-top:12px;"),
                                div().withId("uploadPreview")
                                        .withStyle("border:1px solid #e0e0e0;padding:8px;background:#f6f6f6;"
                                                + "font-family:monospace;font-size:0.8rem;white-space:pre;overflow:auto;max-height:300px;")
                        ),
                        div().withClass("modal-footer").with(
                                a("覆盖 / 创建").withHref("#").withId("btnUploadConfirm")
                                        .withClass("waves-effect indigo btn"),
                                a("取消").withHref("#").withClass("modal-close waves-effect btn-flat")
                        )
                ),
                script().withSrc("/vendor/codemirror/codemirror.js"),
                script().withSrc("/vendor/codemirror/nginx-mode.js"),
                script().withSrc("/js/Config.js?v=" + yi.shi.plinth.App.START_TIME)
        );
    }

    /** CodeMirror 加载失败时的降级文本域（Config.js 检测后自动隐藏其中一个）。 */
    private j2html.tags.specialized.TextareaTag textareaFallback() {
        return j2html.TagCreator.textarea().withId("editorFallback")
                .withClass("materialize-textarea").withStyle("display:none;width:100%;min-height:420px;font-family:monospace;");
    }

    /** Materialize 风格操作按钮（<a id>，JS 绑定 click）。 */
    private j2html.tags.specialized.ATag actionBtn(String text, String color, String id) {
        return a(text).withClass("btn waves-effect waves-light " + color).withId(id)
                .withHref("#").withStyle("margin:4px 4px 4px 0;text-transform:none;");
    }
}
