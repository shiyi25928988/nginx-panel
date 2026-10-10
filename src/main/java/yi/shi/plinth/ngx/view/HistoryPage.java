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
import static j2html.TagCreator.table;
import static j2html.TagCreator.tbody;
import static j2html.TagCreator.th;
import static j2html.TagCreator.thead;
import static j2html.TagCreator.tr;

/**
 * 版本历史页：按文件查看全部历史版本，查看任意两版 diff，一键回滚。
 */
@HttpService
public class HistoryPage extends Page {

    @GET
    @HttpPath("/page/history")
    @AUTH(authUrl = "/page/login")
    public HTML historyPage() throws Exception {
        HTML result = new HTML();
        result.setHtmlContent(createHtml().render());
        return result;
    }

    @Override
    protected HeadTag createHead() throws Exception {
        return Head.createHead("版本历史 - Nginx Shell").with(
                // 左右分栏 diff 样式：修改行左红右绿、行间对齐、行号列
                j2html.TagCreator.style().withText(
                        // 弹层放大 + 深底浅字（显式逐层声明，防止 Materialize 表格默认黑字回退）
                        "#diffModal{width:96% !important;max-width:96vw !important;top:2% !important;max-height:96% !important;}"
                        + "#diffBody{background:#1e1e1e;color:#e6e6e6;height:min(68vh,620px);overflow:auto;padding:4px 0;}"
                        + "#diffBody .diff-table td{color:#e6e6e6;text-align:left;}"
                        + "#diffBody .diff-table{border-collapse:collapse;width:100%;table-layout:fixed;font-family:monospace;font-size:0.82rem;}"
                        + "#diffBody .diff-table th{position:sticky;top:0;background:#252526;color:#9e9e9e;"
                        + "padding:4px 6px;text-align:left;font-weight:400;border-bottom:1px solid #444;z-index:1;}"
                        + "#diffBody .diff-table th.gutter{width:48px;}"
                        + "#diffBody .diff-table td{vertical-align:top;padding:1px 6px;white-space:pre-wrap;word-break:break-all;border-left:1px solid #333;}"
                        + "#diffBody .diff-table td.gutter{width:48px;color:#8a8a8a;text-align:right;user-select:none;background:#252526;}"
                        + " #diffBody tr.diff-left td.cell-old{background:rgba(244,67,54,0.22);}"
                        + " #diffBody tr.diff-right td.cell-new{background:rgba(76,175,80,0.22);}"
                        + "#diffBody tr td.cell-old{border-right:1px solid #555;}"
                        + "#diffBody tr.diff-left td.cell-old, #diffBody tr.diff-right td.cell-new{color:#fff;}"
                )
        );
    }

    @Override
    protected MainTag createMain() throws Exception {
        return main().with(
                div().withClass("container").with(
                        h5("版本历史").withStyle("margin-top:20px;"),
                        div().withClass("row").with(
                                div().withClass("col s12").with(
                                        div().withClass("card").with(
                                                div().withClass("card-content").with(
                                                        span("选择文件").withClass("card-title").withStyle("font-size:1.1rem;"),
                                                        div().withClass("input-field").with(
                                                                j2html.TagCreator.select().withId("historyFileSelect")
                                                                        .withStyle("display:block;")
                                                        ),
                                                        div().with(
                                                                a("加载版本").withHref("#").withId("btnLoadHistory")
                                                                        .withClass("blue btn waves-effect").withStyle("margin:4px;text-transform:none;")
                                                        )
                                                )
                                        )
                                ),
                                div().withClass("col s12").with(
                                        div().withClass("card").with(
                                                div().withClass("card-content").with(
                                                        span("版本列表").withClass("card-title").withStyle("font-size:1.1rem;"),
                                                        span().withId("historyFilePath").withText(""),
                                                        table().withClass("highlight").with(
                                                                thead(tr(th("版本"), th("时间"), th("操作人"), th("原因"), th("MD5"), th("操作"))),
                                                                tbody().withId("historyTableBody")
                                                        ),
                                                        div().with(
                                                                a("上一页").withHref("#").withId("btnHistoryPrev").withClass("btn-flat"),
                                                                span().withId("historyPageInfo"),
                                                                a("下一页").withHref("#").withId("btnHistoryNext").withClass("btn-flat")
                                                        )
                                                )
                                        )
                                )
                        )
                ),
                // diff 弹层
                div().withId("diffModal").withClass("modal modal-fixed-footer").with(
                        div().withClass("modal-content").with(
                                h5("").withId("diffTitle"),
                                a("对比：与当前磁盘").withHref("#").withId("diffToggle")
                                        .withClass("waves-effect btn-flat blue-text").withStyle("font-size:0.8rem;padding:0 6px;"),
                                span().withId("diffMeta"),
                                div().withId("diffBody").withStyle("font-family:monospace;font-size:0.85rem;white-space:pre-wrap;overflow:auto;")
                        ),
                        div().withClass("modal-footer").with(
                                a("关闭").withHref("#").withClass("modal-close waves-effect btn-flat")
                        )
                ),
                script().withSrc("/js/History.js?v=" + yi.shi.plinth.App.START_TIME)
        );
    }
}
