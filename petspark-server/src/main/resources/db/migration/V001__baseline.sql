-- V001 基线：建立平台公共表（审计日志 + 事务 Outbox）。
-- 设计依据：05-数据库设计说明书 §2 audit_log、§9 outbox_event。
-- 仅建公共空表，不写入业务数据，回滚应用后可保留以维持兼容。

CREATE TABLE audit_log (
    id           BIGINT UNSIGNED NOT NULL COMMENT '应用生成的雪花型主键',
    request_id   VARCHAR(64)     NOT NULL COMMENT '请求追踪 ID，与日志/响应信封一致',
    actor_id     VARCHAR(32)     NULL     COMMENT '操作者用户 ID；系统操作为空',
    actor_role   VARCHAR(32)     NOT NULL COMMENT '操作者角色/来源',
    module       VARCHAR(32)     NOT NULL COMMENT '业务模块',
    action       VARCHAR(64)     NOT NULL COMMENT '动作',
    object_type  VARCHAR(32)     NULL     COMMENT '操作对象类型',
    object_id    VARCHAR(32)     NULL     COMMENT '操作对象 ID',
    result       VARCHAR(16)     NOT NULL COMMENT 'SUCCESS/FAILURE',
    reason_code  VARCHAR(64)     NULL     COMMENT '失败原因码，成功为空',
    ip_hash      VARCHAR(64)     NULL     COMMENT '客户端 IP 哈希，不存明文',
    created_at   DATETIME(3)     NOT NULL COMMENT 'UTC 时间戳',
    PRIMARY KEY (id),
    KEY idx_audit_actor_time (actor_id, created_at),
    KEY idx_audit_object (object_type, object_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='审计日志，只追加';

CREATE TABLE outbox_event (
    id              BIGINT UNSIGNED NOT NULL COMMENT '应用生成的雪花型主键',
    event_type      VARCHAR(64)     NOT NULL COMMENT '事件类型',
    aggregate_type  VARCHAR(32)     NOT NULL COMMENT '聚合类型',
    aggregate_id    VARCHAR(32)     NOT NULL COMMENT '聚合 ID',
    payload         JSON            NOT NULL COMMENT '事件负载',
    status          VARCHAR(16)     NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PROCESSING/SENT/DEAD',
    attempt_count   INT             NOT NULL DEFAULT 0 COMMENT '重试次数',
    next_attempt_at DATETIME(3)     NULL     COMMENT '下次尝试时间；PENDING 为空表示待处理',
    created_at      DATETIME(3)     NOT NULL COMMENT '入库时间 UTC',
    processed_at    DATETIME(3)     NULL     COMMENT '处理完成时间',
    PRIMARY KEY (id),
    KEY idx_outbox_pending (status, next_attempt_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='事务 Outbox 事件';
