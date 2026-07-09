-- PR-ADOPT-01: adoption applications, review, and handover closure.
-- 设计依据：05-数据库设计说明书 §领养 与 02A-业务流程与状态模型 §4。
-- 状态机：PENDING → APPROVED → COMPLETED / CANCELLED；PENDING/APPROVED → WITHDRAWN；
--         PENDING → REJECTED。同一宠物仅允许一条未结束申请，由 uk_adoption_pet_active
--         + service 层 SELECT FOR UPDATE pet 行 + 复查配合 pet.version 乐观锁兜底并发。

CREATE TABLE adoption_application (
    id                  VARCHAR(36)  NOT NULL,
    application_no      VARCHAR(64)  NOT NULL COMMENT '展示用申请编号',
    pet_id              VARCHAR(36)  NOT NULL COMMENT '目标宠物',
    applicant_user_id   VARCHAR(36)  NOT NULL COMMENT '申请人',
    statement           VARCHAR(1000) NOT NULL COMMENT '申请说明',
    profile_snapshot    VARCHAR(500) NULL     COMMENT '申请人资料快照（自由填写，不存敏感信息）',
    status              VARCHAR(24)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED/WITHDRAWN/COMPLETED/CANCELLED',
    reviewer_user_id    VARCHAR(36)  NULL     COMMENT '审核人',
    review_note         VARCHAR(500) NULL     COMMENT '审核理由',
    decided_at          DATETIME(3)  NULL     COMMENT '审核决策时间',
    withdrawn_at        DATETIME(3)  NULL     COMMENT '本人撤回时间',
    withdraw_reason     VARCHAR(255) NULL     COMMENT '撤回原因',
    handover_note       VARCHAR(500) NULL     COMMENT '交接备注',
    handover_at         DATETIME(3)  NULL     COMMENT '交接时间',
    idempotency_key     VARCHAR(64)  NULL     COMMENT '申请人级别幂等键',
    version             INT          NOT NULL DEFAULT 0,
    created_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at          DATETIME(3)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_adoption_no (application_no),
    UNIQUE KEY uk_adoption_idem (applicant_user_id, idempotency_key),
    KEY idx_adoption_pet_status (pet_id, status),
    KEY idx_adoption_applicant (applicant_user_id, status),
    KEY idx_adoption_reviewer (reviewer_user_id),
    CONSTRAINT fk_adoption_pet       FOREIGN KEY (pet_id)            REFERENCES pet (id),
    CONSTRAINT fk_adoption_applicant FOREIGN KEY (applicant_user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_adoption_reviewer  FOREIGN KEY (reviewer_user_id)  REFERENCES sys_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='领养申请闭环';

-- adoption:review = 审核领养申请；adoption:handover = 记录线下交接结果。
INSERT IGNORE INTO sys_permission (id, code, resource, action, description)
VALUES
    ('00000000-0000-0000-0000-000000000226', 'adoption:review',   'adoption', 'review',   'Review adoption applications'),
    ('00000000-0000-0000-0000-000000000227', 'adoption:handover', 'adoption', 'handover', 'Record adoption handover results');

-- ADMIN role (...102) 显式绑定本迁移新增的权限；V009 的 blanket SELECT 只覆盖其执行时
-- 已存在的权限，后续迁移新增的权限必须显式绑定才能落到 ADMIN 角色。
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000102', id FROM sys_permission
WHERE code IN ('adoption:review', 'adoption:handover');

-- OP 角色 (...103) 负责运营审核与交接，绑定 adoption:review。
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000103', id FROM sys_permission
WHERE code = 'adoption:review';
