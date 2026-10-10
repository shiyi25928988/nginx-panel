package yi.shi.plinth;

import lombok.extern.slf4j.Slf4j;
import yi.shi.plinth.annotation.PropertiesFile;
import yi.shi.plinth.boot.ServiceBooter;
import yi.shi.plinth.modules.DataSourceModule;

/**
 * Nginx Shell — 轻量级 Nginx 配置控制台。
 *
 * <p>参考 MinIO Shell 的 plinth 技术栈：Guice 7 + Jetty 12 + Sa-Token + MyBatis + H2，
 * 单 fat jar 部署，无需外部数据库。功能：Web 编辑 nginx 配置、保存前校验(nginx -t)、
 * 一键重载(nginx -s reload)、版本可追溯、操作审计日志、内嵌配置模板。
 */
@PropertiesFile(files = { "application.properties" })
@Slf4j
public class App {

    /** 应用启动时间戳，用作静态资源版本号（?v=），重启后浏览器必然重新加载最新 JS/CSS */
    public static final long START_TIME = System.currentTimeMillis();

    public static void main(String[] args) {
        try {
            log.info("启动 Nginx Shell");
            // 启动入口先于配置文件加载构造模块，故 mapper 扫描包在此显式声明（两处：用户 + nginx 业务）
            ServiceBooter.startFrom(App.class,
                    new DataSourceModule("yi.shi.plinth.db.mapper", "yi.shi.plinth.ngx.db.mapper"));
        } catch (Exception e) {
            log.error(e.getMessage());
        }
    }
}
