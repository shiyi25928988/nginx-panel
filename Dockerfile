# ============================================================
# Nginx Shell 应用镜像（依赖基础镜像 nginx-shell-base:1.0）
#   1. mvn package -DskipTests
#   2. docker build -f Dockerfile.base -t nginx-shell-base:1.0 .
#   3. docker build -t nginx-shell:1.0 .
# ============================================================
FROM nginx-shell-base:1.0

WORKDIR /app

COPY target/nginx-shell.jar /app/app.jar
COPY docker/entrypoint-app.sh /app/entrypoint-app.sh
RUN chmod +x /app/entrypoint-app.sh && mkdir -p /app/data

EXPOSE 80 8080

# 数据卷：H2 数据库 + 面板证书目录持久化（建议 compose 挂载 ./data 与 ./certs）
VOLUME ["/app/data", "/app/certs"]

ENTRYPOINT ["/app/entrypoint-app.sh"]
