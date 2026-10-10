-- 审计日志表：记录登录/保存/校验/重载/回滚等全部操作
CREATE TABLE IF NOT EXISTS ngx_audit_log (
    id          BIGINT       AUTO_INCREMENT PRIMARY KEY,
    username    VARCHAR(64)  NOT NULL,
    -- 动作：LOGIN / LOGOUT / SAVE_CONFIG / CREATE_CONFIG / DELETE_CONFIG /
    --       ROLLBACK_CONFIG / APPLY_TEMPLATE / TEST_CONFIG / RELOAD / CHANGE_PASSWORD
    action      VARCHAR(32)  NOT NULL,
    -- 涉及的文件路径（非文件操作为空）
    file_path   VARCHAR(512),
    -- 详情：命令与输出摘要等
    detail      TEXT,
    -- SUCCESS / FAILED
    result      VARCHAR(16)  NOT NULL,
    -- 客户端 IP
    client_ip   VARCHAR(64),
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ngx_audit_time ON ngx_audit_log(id DESC);
