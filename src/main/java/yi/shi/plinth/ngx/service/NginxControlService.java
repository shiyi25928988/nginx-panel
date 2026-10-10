package yi.shi.plinth.ngx.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import yi.shi.plinth.ngx.NginxPanelException;
import yi.shi.plinth.ngx.NginxProperties;
import yi.shi.plinth.ngx.dto.ExecResult;
import yi.shi.plinth.ngx.dto.NginxStatus;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Nginx 进程控制：校验(nginx -t)、重载(nginx -s reload)、版本查询、运行状态探测。
 *
 * <p>两种执行模式：
 * <ul>
 *   <li>{@code native} — 面板与 nginx 同机，直接拉起 nginx 进程；可按配置加 sudo 前缀。</li>
 *   <li>{@code docker} — 通过 {@code docker exec <容器> nginx ...} 进入 nginx 容器执行。</li>
 * </ul>
 *
 * <p>命令均白名单构造（无用户输入拼入 shell 字符串，无注入面）。
 */
@Slf4j
@Singleton
public class NginxControlService {

    /** 命令执行超时（秒） */
    private static final long EXEC_TIMEOUT_SECONDS = 15;
    /** 返回给前端的输出截断长度 */
    private static final int OUTPUT_LIMIT = 8000;

    /** 最近一次 nginx -t 的结果缓存（内存，重启后清零重新探测） */
    private volatile Long lastTestTime;
    private volatile Boolean lastTestOk;

    /** 运行 nginx -t 校验配置，返回是否通过；失败时抛出带输出信息的异常。 */
    public ExecResult test() {
        ExecResult result = exec("test");
        this.lastTestTime = System.currentTimeMillis();
        this.lastTestOk = result.isSuccess();
        return result;
    }

    /** 运行 nginx -s reload，失败时抛异常。 */
    public ExecResult reload() {
        return exec("reload");
    }

    /**
     * docker 模式专用：读取容器内日志文件尾部（docker exec tail -n N <path>，
     * 参数数组直传、不经 shell，path 来自服务端发现清单，无注入面）。
     */
    public ExecResult dockerTail(String containerPath, int lines) throws IOException {
        List<String> command = List.of("docker", "exec", NginxProperties.dockerContainer(),
                "tail", "-n", String.valueOf(lines), containerPath);
        String commandLine = String.join(" ", command);
        log.info("读取容器内日志: {}", commandLine);
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            if (!process.waitFor(EXEC_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return timedOutResult(commandLine);
            }
            ExecResult result = new ExecResult();
            result.setCommand(commandLine);
            result.setExitCode(process.exitValue());
            result.setOutput(truncate(readOutput(process.getInputStream())));
            result.setTimeout(false);
            return result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return timedOutResult(commandLine);
        }
    }

    /** 查询 nginx 版本（nginx -v 输出到 stderr）。 */
    public String version() {
        try {
            ExecResult result = exec("version");
            return result.isSuccess() ? firstLine(result.getOutput()) : result.getOutput();
        } catch (NginxPanelException e) {
            return "未知（" + e.getMessage() + "）";
        }
    }

    /** 查询 nginx 完整版本与编译参数（nginx -V 输出到 stderr，多行）。 */
    public String fullVersion() {
        try {
            ExecResult result = exec("versionFull");
            return result.isSuccess() ? result.getOutput() : result.getOutput();
        } catch (NginxPanelException e) {
            return "不可用（" + e.getMessage() + "）";
        }
    }

    /** 组合状态探测：版本 + 运行中 + 最近校验结果。 */
    public NginxStatus status() {
        NginxStatus status = new NginxStatus();
        status.setConfDir(NginxProperties.confDir());
        status.setExecMode(NginxProperties.execMode());
        if (NginxProperties.isDockerMode()) {
            status.setContainer(NginxProperties.dockerContainer());
            status.setRunning(dockerContainerRunning());
            status.setPid(null);
        } else {
            status.setContainer(null);
            status.setPid(readPid());
            status.setRunning(status.getPid() != null && status.getPid() > 0);
        }
        status.setVersion(version());
        status.setLastTestTime(lastTestTime);
        status.setLastTestOk(lastTestOk);
        return status;
    }

    /** 拼装命令并执行。action ∈ {test, reload, version} → nginx 参数。 */
    private ExecResult exec(String action) {
        List<String> command = buildCommand(action);
        String commandLine = String.join(" ", command);
        log.info("执行 nginx 命令: {}", commandLine);
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            boolean finished = process.waitFor(EXEC_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return timedOutResult(commandLine);
            }
            String output = readOutput(process.getInputStream());
            ExecResult result = new ExecResult();
            result.setCommand(commandLine);
            result.setExitCode(process.exitValue());
            result.setOutput(truncate(output));
            result.setTimeout(false);
            return result;
        } catch (IOException e) {
            // 命令不存在（nginx 未安装/路径错误）等
            log.error("nginx 命令执行失败: {}", e.getMessage());
            throw new NginxPanelException("无法执行 nginx 命令 [" + commandLine + "]：" + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return timedOutResult(commandLine);
        }
    }

    /** 构建命令行。 */
    private List<String> buildCommand(String action) {
        List<String> command = new ArrayList<>();
        if (NginxProperties.isDockerMode()) {
            command.add("docker");
            command.add("exec");
            command.add(NginxProperties.dockerContainer());
            command.add(NginxProperties.dockerBin());
        } else {
            if (NginxProperties.sudo()) {
                command.add("sudo");
                command.add("-n");
            }
            command.add(NginxProperties.bin());
        }
        switch (action) {
            case "test":
                command.add("-t");
                break;
            case "reload":
                command.add("-s");
                command.add("reload");
                break;
            case "version":
                command.add("-v");
                break;
            case "versionFull":
                command.add("-V");
                break;
            default:
                throw new IllegalArgumentException("unknown nginx action: " + action);
        }
        return command;
    }

    /** native 模式：读取 nginx master pid 文件并确认进程存活；不可探测时返回 -1。 */
    private Long readPid() {
        String pidFile = NginxProperties.pidFile();
        if (pidFile == null || pidFile.isBlank()) {
            return null;
        }
        try {
            String content = Files.readString(Path.of(pidFile)).trim();
            long pid = Long.parseLong(content.split("\\s+")[0]);
            return java.lang.ProcessHandle.of(pid).isPresent() ? pid : -1L;
        } catch (Exception e) {
            return -1L;
        }
    }

    /** docker 模式：docker inspect 判断容器运行中。 */
    private Boolean dockerContainerRunning() {
        try {
            ProcessBuilder pb = new ProcessBuilder("docker", "inspect", "-f",
                    "{{.State.Running}}", NginxProperties.dockerContainer());
            pb.redirectErrorStream(true);
            Process process = pb.start();
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return false;
            }
            String output = readOutput(process.getInputStream()).trim();
            return "true".equalsIgnoreCase(output);
        } catch (Exception e) {
            return false;
        }
    }

    private String readOutput(InputStream in) throws IOException {
        byte[] bytes = in.readAllBytes();
        return new String(bytes, Charset.defaultCharset());
    }

    private ExecResult timedOutResult(String commandLine) {
        ExecResult result = new ExecResult();
        result.setCommand(commandLine);
        result.setExitCode(-1);
        result.setTimeout(true);
        result.setOutput("命令执行超时（" + EXEC_TIMEOUT_SECONDS + "s）");
        return result;
    }

    private String truncate(String output) {
        if (output == null || output.length() <= OUTPUT_LIMIT) {
            return output;
        }
        return output.substring(0, OUTPUT_LIMIT) + "\n...(输出已截断)";
    }

    private String firstLine(String output) {
        if (output == null || output.isBlank()) {
            return output;
        }
        return output.split("\r?\n")[0].trim();
    }
}
