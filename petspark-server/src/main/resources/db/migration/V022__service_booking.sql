-- PR-SERVICE-01: 服务资源、可用窗口与通用预约。
-- 设计依据：需求/故事 REQ-SVC-001~003、US-015；接口 API-SVC-001~013。
--
-- 本迁移建立服务领域六张表：
--   service_item           服务项目（带 kind 与透明度字段：资质/时段/异常规则）
--   service_resource       服务资源（执行服务的人/场地/设备）
--   service_slot           可用窗口（资源在某时段的占用单元）
--   service_booking        通用预约（预约状态机，可被后续 kind 复用）
--   service_cancellation   取消/履约轨迹（取消与异常终止的审计行）
--   service_specification  可选服务规格（同一个服务项目的可选规格/价格档）
--
-- 关键不变量：
--   - 同一 service_resource 在同一时间窗口容量上限内可被多人预约，但不可超卖：
--       预约事务内对 service_slot 行 SELECT ... FOR UPDATE 加行锁，
--       并以 booked_count < capacity 为条件 UPDATE 原子增占（返回 0 即满员/不可预约），
--       取消/异常终止时 booked_count - 1 原子释放窗口（同一窗口可被后续预约复用）。
--       service_slot.booked_count 受 chk CHECK 约束在 [0, capacity] 区间兜底。
--   - service_item.kind 声明服务类别（GENERIC/TRAINING/BEAUTY/MEDICAL），
--       本次只实现 GENERIC + 通用预约，后续 PR 复用本表与状态机。
--   - 透明度字段：资质(qualification)、时段说明(availability_note)、异常规则(exception_rule)
--       在 service_item/service_resource 上以明文落库，前端只读展示。
--   - 取消/异常终止写 service_cancellation 保留轨迹并释放窗口。
--   - 通知走 NotificationService.send 落 outbox，不在迁移里插 notification_type 静态行。

CREATE TABLE service_item (
    id VARCHAR(36) NOT NULL,
    kind VARCHAR(16) NOT NULL DEFAULT 'GENERIC',
    code VARCHAR(64) NOT NULL,
    name VARCHAR(120) NOT NULL,
    description TEXT NULL,
    qualification VARCHAR(500) NULL,
    availability_note VARCHAR(500) NULL,
    exception_rule VARCHAR(500) NULL,
    base_price DECIMAL(12,2) NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    version INT NOT NULL DEFAULT 0,
    deleted_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_service_item_code (code),
    KEY idx_service_item_kind_status (kind, status),
    CONSTRAINT chk_service_item_kind CHECK (kind IN ('GENERIC','TRAINING','BEAUTY','MEDICAL')),
    CONSTRAINT chk_service_item_price CHECK (base_price >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Service item with kind and transparency fields';

CREATE TABLE service_specification (
    id VARCHAR(36) NOT NULL,
    service_item_id VARCHAR(36) NOT NULL,
    name VARCHAR(120) NOT NULL,
    price_delta DECIMAL(12,2) NOT NULL DEFAULT 0,
    sort_order INT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_spec_item (service_item_id),
    CONSTRAINT fk_spec_item FOREIGN KEY (service_item_id) REFERENCES service_item (id),
    CONSTRAINT chk_spec_price_delta CHECK (price_delta >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Optional service specification tiers';

CREATE TABLE service_resource (
    id VARCHAR(36) NOT NULL,
    service_item_id VARCHAR(36) NOT NULL,
    name VARCHAR(120) NOT NULL,
    qualification VARCHAR(500) NULL,
    availability_note VARCHAR(500) NULL,
    exception_rule VARCHAR(500) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    capacity INT NOT NULL DEFAULT 1,
    version INT NOT NULL DEFAULT 0,
    deleted_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_resource_item_status (service_item_id, status),
    CONSTRAINT fk_resource_item FOREIGN KEY (service_item_id) REFERENCES service_item (id),
    CONSTRAINT chk_resource_capacity CHECK (capacity >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Service resource (person/venue/equipment)';

CREATE TABLE service_slot (
    id VARCHAR(36) NOT NULL,
    resource_id VARCHAR(36) NOT NULL,
    slot_date DATE NOT NULL,
    start_at DATETIME(3) NOT NULL,
    end_at DATETIME(3) NOT NULL,
    capacity INT NOT NULL DEFAULT 1,
    booked_count INT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    version INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_slot_resource_time (resource_id, start_at),
    KEY idx_slot_date (slot_date, status),
    CONSTRAINT fk_slot_resource FOREIGN KEY (resource_id) REFERENCES service_resource (id),
    CONSTRAINT chk_slot_time CHECK (end_at > start_at),
    CONSTRAINT chk_slot_capacity CHECK (capacity >= 1),
    CONSTRAINT chk_slot_booked CHECK (booked_count >= 0 AND booked_count <= capacity)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Available service slot window';

CREATE TABLE service_booking (
    id VARCHAR(36) NOT NULL,
    booking_no VARCHAR(64) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    pet_id VARCHAR(36) NULL,
    service_item_id VARCHAR(36) NOT NULL,
    specification_id VARCHAR(36) NULL,
    resource_id VARCHAR(36) NOT NULL,
    slot_id VARCHAR(36) NOT NULL,
    kind VARCHAR(16) NOT NULL DEFAULT 'GENERIC',
    status VARCHAR(16) NOT NULL DEFAULT 'CREATED',
    start_at DATETIME(3) NOT NULL,
    end_at DATETIME(3) NOT NULL,
    unit_price DECIMAL(12,2) NOT NULL DEFAULT 0,
    customer_name VARCHAR(64) NOT NULL,
    customer_phone_ciphertext VARCHAR(255) NOT NULL,
    remark VARCHAR(500) NULL,
    cancel_reason VARCHAR(255) NULL,
    cancelled_at DATETIME(3) NULL,
    fulfilled_at DATETIME(3) NULL,
    exception_note VARCHAR(255) NULL,
    version INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_booking_no (booking_no),
    -- 不在 (resource_id, slot_id) 建全局唯一索引：取消后允许重新预约同一窗口。
    -- 重叠防护由 service_slot.booked_count 的条件 UPDATE（FOR UPDATE + 状态前置）
    -- 在事务内原子保证：同一窗口只能有一次确认占用，取消时回退 booked_count 释放窗口。
    KEY idx_booking_user_status (user_id, status),
    KEY idx_booking_item (service_item_id),
    KEY idx_booking_slot (slot_id),
    CONSTRAINT fk_booking_user FOREIGN KEY (user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_booking_pet FOREIGN KEY (pet_id) REFERENCES pet (id),
    CONSTRAINT fk_booking_item FOREIGN KEY (service_item_id) REFERENCES service_item (id),
    CONSTRAINT fk_booking_resource FOREIGN KEY (resource_id) REFERENCES service_resource (id),
    CONSTRAINT fk_booking_slot FOREIGN KEY (slot_id) REFERENCES service_slot (id),
    CONSTRAINT chk_booking_time CHECK (end_at > start_at),
    CONSTRAINT chk_booking_price CHECK (unit_price >= 0),
    CONSTRAINT chk_booking_kind CHECK (kind IN ('GENERIC','TRAINING','BEAUTY','MEDICAL')),
    CONSTRAINT chk_booking_status CHECK (status IN ('CREATED','CONFIRMED','IN_PROGRESS','COMPLETED','CANCELLED','EXCEPTION'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Generic service booking with overlap guard';

CREATE TABLE service_cancellation (
    id VARCHAR(36) NOT NULL,
    booking_id VARCHAR(36) NOT NULL,
    reason VARCHAR(255) NOT NULL,
    cancellation_type VARCHAR(16) NOT NULL,
    operator_id VARCHAR(36) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_cancellation_booking (booking_id, created_at),
    KEY idx_cancellation_operator (operator_id, created_at),
    CONSTRAINT fk_cancellation_booking FOREIGN KEY (booking_id) REFERENCES service_booking (id),
    CONSTRAINT fk_cancellation_operator FOREIGN KEY (operator_id) REFERENCES sys_user (id),
    CONSTRAINT chk_cancellation_type CHECK (cancellation_type IN ('CANCEL','EXCEPTION'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Service booking cancellation and exception trail';

-- service:manage = 管理服务项目/资源/窗口/履约；service:fulfill = 服务履约角色执行预约流转。
-- 复用已有 BOOKING_CONFLICT_001 错误码（V018 前已存在），不在此重新定义。
INSERT IGNORE INTO sys_permission (id, code, resource, action, description)
VALUES
    ('00000000-0000-0000-0000-000000000226', 'service:manage', 'service', 'manage', 'Manage service items, resources, slots and bookings'),
    ('00000000-0000-0000-0000-000000000227', 'service:fulfill', 'service', 'fulfill', 'Transition service bookings to fulfillment and complete');

-- ADMIN (...102) 显式绑定新引入权限（V009 的 blanket SELECT 未覆盖后续迁移新增的权限）。
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000102', id FROM sys_permission
WHERE code IN ('service:manage', 'service:fulfill');

-- SERVICE 角色 (...104) 绑定服务履约权限，使服务角色可流转预约状态。
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000104', id FROM sys_permission
WHERE code IN ('service:fulfill', 'service:manage');
