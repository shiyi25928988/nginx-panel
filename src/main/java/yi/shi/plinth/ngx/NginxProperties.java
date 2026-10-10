package yi.shi.plinth.ngx;

import yi.shi.plinth.properties.CoreProperties;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Nginx 相关配置（读取系统属性，来自 application.properties / -D / 环境变量映射）。
 */
public final class NginxProperties {

    public static final String MODE_NATIVE = "native";
    public static final String MODE_DOCKER = "docker";

    private NginxProperties() {
    }

    /** nginx 配置根目录 */
    public static String confDir() {
        return CoreProperties.getProperties("nginx.conf.dir");
    }

    /** 面板证书工作目录（上传/自签统一收口，默认 /app/certs；系统证书目录 /etc/ssl 禁止混用） */
    public static String certDir() {
        return CoreProperties.getProperties("cert.dir", "/app/certs");
    }

    public static Path certDirPath() {
        return Paths.get(certDir());
    }

    public static Path confDirPath() {
        return Paths.get(confDir());
    }

    /** nginx 可执行文件（native 模式） */
    public static String bin() {
        return CoreProperties.getProperties("nginx.bin", "nginx");
    }

    /** 执行模式：native / docker */
    public static String execMode() {
        return CoreProperties.getProperties("nginx.exec.mode", MODE_NATIVE);
    }

    public static boolean isDockerMode() {
        return MODE_DOCKER.equalsIgnoreCase(execMode());
    }

    /** docker 模式下的 nginx 容器名 */
    public static String dockerContainer() {
        return CoreProperties.getProperties("nginx.docker.container", "nginx");
    }

    /** docker 模式下容器内 nginx 可执行文件路径 */
    public static String dockerBin() {
        return CoreProperties.getProperties("nginx.docker.bin", "nginx");
    }

    /** native 模式下命令是否加 sudo 前缀 */
    public static boolean sudo() {
        return Boolean.parseBoolean(CoreProperties.getProperties("nginx.sudo", "false"));
    }

    /** nginx master pid 文件（native 模式探测运行状态），返回空串表示跳过检测 */
    public static String pidFile() {
        return CoreProperties.getProperties("nginx.pid.file", "/run/nginx.pid");
    }
}
