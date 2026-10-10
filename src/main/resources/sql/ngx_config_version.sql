-- 配置文件版本表：每次保存/回滚成功后写入一条，内容全文保存，支持追溯与回滚
CREATE TABLE IF NOT EXISTS ngx_config_version (
    id          BIGINT       AUTO_INCREMENT PRIMARY KEY,
    file_path   VARCHAR(512) NOT NULL,
    -- 本次保存前的文件内容（新建文件时为 NULL）
    old_content TEXT,
    -- 本次保存后的文件内容
    new_content TEXT,
    -- new_content 的 MD5，快速判断内容是否有变化
    md5         VARCHAR(32)  NOT NULL,
    -- 文件内递增版本号
    version_no  INT          NOT NULL,
    -- 操作人
    editor      VARCHAR(64)  NOT NULL,
    -- 修改原因/备注
    reason      VARCHAR(512),
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ngx_version_path ON ngx_config_version(file_path, id DESC);
