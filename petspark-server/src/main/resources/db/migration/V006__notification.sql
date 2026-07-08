-- PR-NOTIFY-01: in-app notifications materialized from the transactional outbox.
-- 设计依据：05-数据库设计说明书 §9 notification。
-- 通知只有 UNREAD/READ 语义：read_at 非空即已读，不提供恢复未读。
-- 通知行由 OutboxDispatcher 从 outbox_event 载荷异步落库，业务事务只写 outbox 事件，
-- 因此通知投递失败不会回滚业务（验收：业务成功不因通知发送失败回滚）。

CREATE TABLE notification (
    id            VARCHAR(36)  NOT NULL COMMENT '通知 ID，与触发它的 outbox 事件 ID 一致，保证投递幂等',
    recipient_id  VARCHAR(36)  NOT NULL COMMENT '接收者用户 ID',
    type          VARCHAR(32)  NOT NULL COMMENT '通知类型，例如 SYSTEM、ADOPTION_RESULT',
    title         VARCHAR(128) NOT NULL COMMENT '通知标题',
    content       VARCHAR(512) NOT NULL COMMENT '通知正文',
    business_type VARCHAR(64)  NULL     COMMENT '关联业务类型，可空',
    business_id   VARCHAR(64)  NULL     COMMENT '关联业务对象 ID，可空',
    read_at       DATETIME(3)  NULL     COMMENT '已读时间；NULL 表示未读，置位后不可恢复',
    created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '落库时间 UTC',
    PRIMARY KEY (id),
    KEY idx_notify_recipient (recipient_id, read_at, created_at),
    CONSTRAINT fk_notification_recipient FOREIGN KEY (recipient_id) REFERENCES sys_user (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='站内通知，按接收者隔离';
