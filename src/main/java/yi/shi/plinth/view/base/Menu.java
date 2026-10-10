package yi.shi.plinth.view.base;

import cn.dev33.satoken.stp.StpUtil;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 导航菜单，按登录态动态生成（单管理员，全部页面登录即可见）。
 */
public class Menu {

    public static Map<String, String> getMenu() {
        Map<String, String> menu = new LinkedHashMap<>();
        if (StpUtil.isLogin()) {
            menu.put("控制台", "/page/index");
            menu.put("服务状态", "/page/status");
            menu.put("网络拓扑", "/page/topology");
            menu.put("配置文件", "/page/config");
            menu.put("Nginx日志", "/page/logs");
            menu.put("证书管理", "/page/certs");
            menu.put("版本历史", "/page/history");
            menu.put("模板", "/page/templates");
            menu.put("操作日志", "/page/audit");
            menu.put("修改密码", "/page/profile");
            menu.put("退出", "/user/logout");
        } else {
            menu.put("登录", "/page/login");
        }
        return menu;
    }
}
