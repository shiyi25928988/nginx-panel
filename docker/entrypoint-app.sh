#!/bin/bash
# ============================================================
# Nginx Shell 应用镜像入口：先拉起 nginx，再前台运行面板
#   - 面板以 native 模式控制本容器内的 nginx（nginx -t / -s reload）
#   - 面板退出时向 nginx 发出平滑退出信号
# ============================================================
set -e

# 允许通过 NEEDS_NGINX=false 让 nginx 由外部（如 supervisord/systemd）管理
if [ "${NEEDS_NGINX:-true}" = "true" ]; then
    echo "[entrypoint] starting nginx ..."
    nginx
fi

echo "[entrypoint] starting nginx-shell panel on port ${SERVER_PORT:-8080} ..."
java \
  -Dserver.port="${SERVER_PORT:-8080}" \
  -Dserver.idleTimeout="${SERVER_IDLE_TIMEOUT:-300000}" \
  -Dadmin.default.username="${ADMIN_DEFAULT_USERNAME:-admin}" \
  -Dadmin.default.password="${ADMIN_DEFAULT_PASSWORD:-admin123}" \
  -DJDBC.driver=org.h2.Driver \
  -DJDBC.url="${JDBC_URL:-jdbc:h2:file:/app/data/nginx_shell;MODE=MySQL;DB_CLOSE_DELAY=-1}" \
  -DJDBC.username=sa -DJDBC.password= \
  -Dnginx.conf.dir="${NGINX_CONF_DIR:-/etc/nginx}" \
  -Dnginx.bin="${NGINX_BIN:-/usr/sbin/nginx}" \
  -Dnginx.exec.mode="${NGINX_EXEC_MODE:-native}" \
  -Dcert.dir="${CERT_DIR:-/app/certs}" \
  -Dnginx.pid.file="${NGINX_PID_FILE:-/run/nginx.pid}" \
  -Dnginx.sudo=false \
  $JAVA_OPTS \
  -jar /app/app.jar &
PANEL_PID=$!

graceful_shutdown() {
    echo "[entrypoint] shutting down ..."
    nginx -s quit 2>/dev/null || true
    kill "$PANEL_PID" 2>/dev/null || true
    wait "$PANEL_PID" 2>/dev/null || true
}
trap graceful_shutdown SIGTERM SIGINT

# 前台等待面板进程；面板异常退出则容器退出（交给 restart 策略）
wait "$PANEL_PID"
