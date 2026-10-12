# Nginx Shell

<p align="left">
  <img alt="License" src="https://img.shields.io/badge/license-MIT-blue.svg">
  <img alt="Java" src="https://img.shields.io/badge/Java-21-orange.svg">
  <img alt="Jetty" src="https://img.shields.io/badge/Jetty%2012-green.svg">
  <img alt="Nginx" src="https://img.shields.io/badge/Nginx-native%20%7C%20docker-2f9e44.svg">
</p>

**Nginx 配置控制台** — 单管理员、部署在 nginx 所在服务器（或同容器）上的轻量级 Web 管理面板。改配置 → 自动 `nginx -t` 校验 → 一键 `reload`，全程版本可追溯、执行有审计。技术栈自研自 [MinIO Shell](https://github.com/shiyi25928988/minio-shell)（plinth：Guice 7 + Jetty 12 + H2），无 Spring Boot、无外部依赖，单 fat jar 开箱即用。

## 界面一览

### 控制台

nginx 运行状态一眼可见（版本 / 配置目录 / 执行模式 / PID / 最近校验结果），最近操作日志与最近配置改动在此汇总，常用动作一键直达。

![控制台](docs/screenshots/console.png)

### 服务状态面板

nginx master 存活 / 运行时长 / 完整编译参数（nginx -V），宿主机 CPU / 内存 / 磁盘分区实时进度条，每 15 秒自动刷新。

![服务状态](docs/screenshots/status.png)

## 功能特性

**配置文件**
- 在线编辑 — 配置目录文件树 + CodeMirror 高亮（自研 nginx 词法模式，修掉原生模式路径/正则整段标红问题），编辑器可一键全屏放大；保存自动 `nginx -t`，失败自动还原原内容（新文件则删除）——磁盘上的配置永远处于"能被 nginx 正常加载"的状态。
- 候选内容校验 — 编辑器里**未保存**的内容（nginx.conf / conf.d 片段 / 新建文件）可先校验后保存：临时把候选写入真实位置参与整树 `nginx -t`，用完即还原，不落盘。
- 上传整体覆盖 — 本地 `.conf` 直接覆盖目标文件，管理员密码二次确认，上传前后的完整 diff 自动进版本记录。

**生效与追溯**
- 一键生效 — `nginx -s reload`，保存时可勾选"成功后自动重载"。
- 版本可追溯 — 每次保存/上传/删除记录修改前后全文 + MD5 + 操作人 + 原因；左右分栏 diff（修改行左红右绿、带行号、可切换与当前磁盘对比）。有意不提供一键回滚：恢复旧内容请复制版本内容到编辑器经保存校验流水线完成，多一道人工确认。
- 操作审计日志 — 登录/保存/删除/模板/校验/重载等全动作留痕（含客户端 IP 与命令输出摘要），按动作筛选、分页查询。

**运维面板**
- 服务状态 — nginx 进程存活/运行时长/完整编译版本，宿主机 CPU、内存、磁盘分区实时进度条，15 秒自动刷新；纯 JDK 能力实现，零依赖。
- Nginx 日志 — `error_log`/`access_log` **动态发现**（解析配置指令 + `nginx -V` 编译默认路径，改配置重载后自动跟随）；尾部 100-5000 行只读文件尾（大文件不吃内存）、关键字过滤、自动刷新；docker 模式经 `docker exec tail` 无 shell 读容器内日志；tail 路径限定在发现清单内，杜绝任意文件读取。

**HTTPS / 模板**
- 证书管理 — 证书清单由 `ssl_certificate`/`ssl_certificate_key` 指令自动发现：CN/SAN/签发者/有效期/私钥签名挑战匹配检测，<7 天红、<30 天橙到期预警；证书统一收口 `cert.dir`（默认 `/app/certs`），**系统证书目录（/etc/ssl）不参与面板替换**；替换需管理员密码二次确认，上传前预检 PEM 与配对性，替换后 `nginx -t` 失败自动还原；内置自签证书生成（RSA2048/SHA256/SAN）。
- 内嵌模板 — 主配置骨架、静态站点、HTTP 反代、WebSocket 反代、负载均衡、HTTPS、Gzip+安全头、资源缓存 8 套模板，`{{变量}}` 表单填写 + 实时预览，生成结果同样走校验流水线。

**部署形态**
- `native` — 面板与 nginx 同机，直接读写配置目录、拉起 nginx 进程（可选 sudo 前缀）。
- `docker` 一体式 — 基础镜像同时装好 JDK 21 + nginx，容器内面板以 `nginx -t/-s reload` 管理同容器 master。
- `docker exec` — 面板与 nginx 分容器，经 `docker exec <nginx容器> nginx ...` 控制。

**安全与依赖**
- 路径白名单（所有文件操作锁死在 `cert.dir`/`conf.dir` 内，命令只允许 `nginx -t/-v/-s reload` 且 ProcessBuilder 数组启动不经 shell）、单管理员 + 会话认证、原子写入防半份配置。
- 单 fat jar + 嵌入式 H2，首次启动自动创建管理员（`admin/admin123`，仅空库生效，上线请改密）。

## 架构

| 层 | 技术 |
|---|---|
| 运行容器 | Jetty 12（内嵌，EE10 Servlet 6.1） |
| IoC / 分发 | Guice 7 + 自研 plinth 框架（`@HttpService`/`@HttpPath` 注解扫描分发） |
| 认证 | Sa-Token 1.44（单管理员，`@AUTH(orRole="admin")`） |
| ORM / 存储 | MyBatis 3 + mybatis-guice + H2 2.2 |
| 页面 | j2html 服务端渲染 + Materialize + jQuery + CodeMirror 5 |
| 构建 | Java 21 + maven-shade-plugin 单 fat jar |

## 快速开始

### 1. 构建单 jar

```bash
mvn package -DskipTests
# 产物: target/nginx-shell.jar
```

### 2. 部署到 nginx 所在服务器（native 模式，推荐）

```bash
sudo mkdir -p /opt/nginx-shell
sudo cp target/nginx-shell.jar /opt/nginx-shell/
sudo cp deploy/nginx-shell.service /etc/systemd/system/
sudo systemctl daemon-reload && sudo systemctl enable --now nginx-shell
```

访问 `http://<server>:8080/page/login`（首次账号 `admin / admin123`，请先到"修改密码"修改）。

权限说明（详见 `deploy/nginx-shell.service` 头部注释）：
- 面板以 root 运行（默认）→ 直接读写 `/etc/nginx` 并执行 nginx 命令；
- 面板以普通用户运行 → 设置 `nginx.sudo=true`，该用户需配置仅限定 nginx 命令的免密 sudo。

### 3. 本地调试

```bash
java -Dnginx.conf.dir=D:/nginx/nginx/conf -Dnginx.bin=D:/nginx/nginx/nginx.exe -jar target/nginx-shell.jar
```

（Windows 下方便本地开发联调；`nginx.pid.file` 置空可跳过运行状态检测。）

### 4. Docker 一体式部署（推荐容器方案：面板 + nginx 同容器）

基础镜像已包含 **JDK 21 + nginx**，不需要外部数据库与 docker sock：

```bash
# 1. 构建基础镜像（eclipse-temurin 21-jre + Ubuntu nginx）
docker build -f Dockerfile.base -t nginx-shell-base:1.0 .
# 2. 构建应用镜像（panel jar + entrypoint）
mvn package -DskipTests
docker build -t nginx-shell:1.0 .
# 3. 运行（容器内 8080=面板，80=nginx）
docker run -d --name nginx-shell -p 8080:8080 -p 80:80 \
  -v ./data:/app/data nginx-shell:1.0
#    或使用一体式编排：
docker compose up -d
```

容器内为 native 模式：面板以 root 运行 nginx（`/usr/sbin/nginx`），`nginx -t`/`-s reload` 直接作用于容器内的 nginx 主进程；`./data` 卷持久化 H2（版本历史 + 审计日志），容器重建数据不丢。宿主机 CPU 不支持 x86-64-v2 指令集时不能用 nginx.org 官方包（会报 `CPU ISA level is lower than required`），基础镜像已改用 Ubuntu 发行版 nginx（编译目标为基线 x86-64）。

> Ubuntu 发行版 nginx 默认布局带 `sites-enabled/default`（与 conf.d 冲突时后者被 ignored），一体部署后建议通过面板编辑 `nginx.conf` 关闭 `sites-enabled` 相关 include，或删掉默认站点符号链接。

### 5. 容器化替换宿主机 nginx（Linux 生产部署推荐）

`docker-compose.linux.yml` 一站式接管：`network_mode: host` 直接监听宿主 80/443（替代二进制安装的 nginx）、原 nginx 配置迁至 `/data/nginx-shell/nginx`、日志与面板数据（jar/H2/证书）全持久化到宿主；迁移引导（停旧 nginx、config 迁移、/app 引导）见文件头部注释。旧 nginx 务必先 `systemctl stop/disable` 释放端口。

### 6. docker exec 模式（面板与 nginx 分容器，可选）

旧模式保留：面板容器挂 `/var/run/docker.sock` + docker CLI，经 `docker exec <nginx容器> nginx -t/-s reload` 控制同 compose 内的 nginx 容器。需要时把 entrypoint 换回 `docker exec` 形态并设 `NGINX_EXEC_MODE=docker`。

## 配置项

全部可通过 `application.properties`、`-D` 系统属性或环境变量（`docker/entrypoint-app.sh` 映射）设置：

| 配置 | 默认 | 说明 |
|---|---|---|
| `server.port` | 8080 | 面板端口 |
| `nginx.conf.dir` | /etc/nginx | nginx 配置根目录（文件树/读写范围） |
| `cert.dir` | /app/certs | 面板证书工作目录（上传/自签统一收口，禁触 /etc/ssl） |
| `nginx.bin` | nginx | native 模式 nginx 可执行文件路径 |
| `nginx.exec.mode` | native | `native` / `docker` |
| `nginx.docker.container` | nginx | docker 模式容器名 |
| `nginx.pid.file` | /run/nginx.pid | native 模式运行状态探测（置空跳过） |
| `nginx.sudo` | false | native 命令加 `sudo -n` 前缀 |
| `admin.default.username/password` | admin/admin123 | 内置管理员（仅空库首次创建生效） |
| `server.ssl.enabled/host` | false | 面板 HTTPS（自签名证书自动生成） |

## 反向代理（可选：让面板走 nginx 的 443）

```nginx
server {
    listen 443 ssl;
    server_name panel.example.com;
    # 证书...
    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

（审计日志的记录端 IP 会识别 `X-Forwarded-For`。）

## 安全设计要点

- **路径白名单**：所有文件操作仅限 `nginx.conf.dir` 目录内，拒绝 `..` / 绝对路径 / 反斜杠注入；写操作仅限 `.conf`/`.types`/`mime.types`，删除仅限 `.conf`。
- **命令白名单**：执行的只有 `nginx -t / -v / -s reload`，参数固定、无用户输入拼入，ProcessBuilder 数组方式启动（不经 shell）。
- **原子写入**：同目录临时文件 + `ATOMIC_MOVE`，断电/崩溃不留半份配置。
- **校验前置**：任何写操作（保存/回滚/删除/模板生成）都以 `nginx -t` 通过为准入条件，失败自动回滚原内容。
- **单管理员 + 会话**：内置唯一管理员，简化权限面；修改密码后强制重新登录。
- **审计全留痕**：所有管理动作（含失败尝试）入 H2 库 `ngx_audit_log`，页面可查。

## 目录结构

```
src/main/java/yi/shi/plinth/
├── annotation/ auth/ boot/ cert/ db/ http/ jetty/    # plinth 框架（来自 MinIO Shell）
├── modules/ properties/ reflection/ rest/ servlet/    # plinth 框架
├── user/                                              # 用户模块（单管理员）
├── view/                                              # 页面基类/组件/静态资源
└── ngx/                                               # ★ Nginx Shell 业务
    ├── NginxProperties / NginxPanelException
    ├── api/      # StatusApi ConfigApi HistoryApi LogApi CertApi AuditApi TemplateApi NginxApi
    ├── db/       # ConfigVersion / AuditLog 实体与 Mapper
    ├── dto/      # SaveResult / FileNode / NginxStatus / CertView / LogFile ...
    ├── service/  # ConfigFileService(校验流水线) NginxControlService HostInfoService
    │             #   LogService CertService VersionService AuditService TemplateService
    └── view/     # 控制台/状态/配置编辑/日志/证书/历史/模板/审计/资料 页面
src/main/resources/
├── sql/         # 建表脚本（幂等）
├── templates/   # 内嵌配置模板（{{VAR}} 占位符）
└── js/nginx-shell-mode.js  # 自研 CodeMirror nginx 高亮模式
Dockerfile.base（JDK21+nginx）/ Dockerfile / docker/ / docker-compose.yml / deploy/
```

## 许可

MIT
