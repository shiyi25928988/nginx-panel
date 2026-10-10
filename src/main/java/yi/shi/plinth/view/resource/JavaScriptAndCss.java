package yi.shi.plinth.view.resource;

import yi.shi.plinth.annotation.http.HttpPath;
import yi.shi.plinth.annotation.http.HttpService;
import yi.shi.plinth.annotation.http.Method.GET;
import yi.shi.plinth.http.MimeType;
import yi.shi.plinth.http.result.BINARY;

/**
 * 暴露 /js/*.js 自定义脚本(每加一个 JS 文件加一个方法)。
 */
@HttpService
public class JavaScriptAndCss {

    @GET
    @HttpPath("/js/Init.js")
    public BINARY initJs() {
        return load("/js/Init.js");
    }

    @GET
    @HttpPath("/js/SideNav.js")
    public BINARY sideNavJs() {
        return load("/js/SideNav.js");
    }

    @GET
    @HttpPath("/js/Login.js")
    public BINARY loginJs() {
        return load("/js/Login.js");
    }

    @GET
    @HttpPath("/js/Console.js")
    public BINARY consoleJs() {
        return load("/js/Console.js");
    }

    @GET
    @HttpPath("/js/Status.js")
    public BINARY statusJs() {
        return load("/js/Status.js");
    }

    @GET
    @HttpPath("/js/Logs.js")
    public BINARY logsJs() {
        return load("/js/Logs.js");
    }

    @GET
    @HttpPath("/js/Topology.js")
    public BINARY topologyJs() {
        return load("/js/Topology.js");
    }

    @GET
    @HttpPath("/js/Certs.js")
    public BINARY certsJs() {
        return load("/js/Certs.js");
    }

    @GET
    @HttpPath("/js/Config.js")
    public BINARY configJs() {
        return load("/js/Config.js");
    }

    @GET
    @HttpPath("/js/History.js")
    public BINARY historyJs() {
        return load("/js/History.js");
    }

    @GET
    @HttpPath("/js/Audit.js")
    public BINARY auditJs() {
        return load("/js/Audit.js");
    }

    @GET
    @HttpPath("/js/Templates.js")
    public BINARY templatesJs() {
        return load("/js/Templates.js");
    }

    @GET
    @HttpPath("/js/Profile.js")
    public BINARY profileJs() {
        return load("/js/Profile.js");
    }

    private BINARY load(String path) {
        BINARY result = new BINARY();
        result.setMimeType(MimeType.APPLICATION_JAVASCRIPT);
        result.setData(this.getClass().getResourceAsStream(path));
        return result;
    }
}
