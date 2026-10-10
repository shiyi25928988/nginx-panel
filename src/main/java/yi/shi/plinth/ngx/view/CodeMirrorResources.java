package yi.shi.plinth.ngx.view;

import yi.shi.plinth.annotation.http.HttpPath;
import yi.shi.plinth.annotation.http.HttpService;
import yi.shi.plinth.annotation.http.Method.GET;
import yi.shi.plinth.http.MimeType;
import yi.shi.plinth.http.result.BINARY;

/**
 * 暴露 CodeMirror 5 webjar（nginx 模式高亮 + material-darker 主题）。
 * 前端在 CodeMirror 未加载时自动降级为纯 textarea 编辑。
 */
@HttpService
public class CodeMirrorResources {

    private static final String CM_BASE = "/META-INF/resources/webjars/codemirror/5.65.16";

    @GET
    @HttpPath("/vendor/codemirror/codemirror.js")
    public BINARY lib() {
        return js(CM_BASE + "/lib/codemirror.js");
    }

    @GET
    @HttpPath("/vendor/codemirror/codemirror.css")
    public BINARY libCss() {
        return css(CM_BASE + "/lib/codemirror.css");
    }

    /** nginx 高亮模式：使用自研轻量词法（webjar 自带 mode/nginx 对路径/glob 误报 cm-error） */
    @GET
    @HttpPath("/vendor/codemirror/nginx-mode.js")
    public BINARY nginxMode() {
        BINARY result = new BINARY();
        result.setMimeType(MimeType.APPLICATION_JAVASCRIPT);
        result.setData(this.getClass().getResourceAsStream("/js/nginx-shell-mode.js"));
        return result;
    }

    @GET
    @HttpPath("/vendor/codemirror/material-darker.css")
    public BINARY themeCss() {
        return css(CM_BASE + "/theme/material-darker.css");
    }

    private BINARY js(String path) {
        BINARY result = new BINARY();
        result.setMimeType(MimeType.APPLICATION_JAVASCRIPT);
        result.setData(this.getClass().getResourceAsStream(path));
        return result;
    }

    private BINARY css(String path) {
        BINARY result = new BINARY();
        result.setMimeType(MimeType.TEXT_CSS);
        result.setData(this.getClass().getResourceAsStream(path));
        return result;
    }
}
