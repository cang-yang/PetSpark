-- PR-BOARD-01: boarding rooms, daily capacity, bookings, and care profiles.
-- 设计依据：05-数据库设计说明书 §5 寄养。
-- 日期区间左闭右开 [start_date, end_date)；创建/取消预约时按日期升序锁定
-- boarding_room_day 行（SELECT ... FOR UPDATE）+ 校验剩余容量，保证多日锁顺序、
-- 不超容量、取消完整释放。只有 CONFIRMED/IN_SERVICE 状态占用房间容量，
-- 占用切换在 assign（CONFIRMED）与 cancel/terminate（释放）同一事务内完成。
-- 状态机：PENDING_CONFIRMATION → CONFIRMED → IN_SERVICE → COMPLETED；
--        确认前可 REJECTED；允许阶段可 CANCELLED；IN_SERVICE 可 TERMINATED。
-- 通知事件用 Boarding<event> 文本，经 NotificationService 落 outbox，不在迁移里插入
-- notification_type 静态行。

CREATE TABLE boarding_room (
    id VARCHAR(36) NOT NULL,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(120) NOT NULL,
    capacity INT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    description VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    deleted_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_room_code (code),
    KEY idx_room_status (status),
    CONSTRAINT chk_room_capacity_positive CHECK (capacity > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Boarding room resource with daily capacity';

CREATE TABLE boarding_room_day (
    id VARCHAR(36) NOT NULL,
    room_id VARCHAR(36) NOT NULL,
    stay_date DATE NOT NULL,
    reserved_count INT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_room_day (room_id, stay_date),
    KEY idx_room_day_date (stay_date),
    CONSTRAINT fk_room_day_room FOREIGN KEY (room_id) REFERENCES boarding_room (id),
    CONSTRAINT chk_room_day_reserved_non_negative CHECK (reserved_count >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Per-room per-day reserved count for capacity enforcement';

CREATE TABLE boarding_booking (
    id VARCHAR(36) NOT NULL,
    booking_no VARCHAR(64) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    pet_id VARCHAR(36) NOT NULL,
    room_id VARCHAR(36) NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING_CONFIRMATION',
    care_profile_id VARCHAR(36) NULL,
    quoted_amount DECIMAL(12,2) NULL,
    cancel_reason VARCHAR(255) NULL,
    reject_reason VARCHAR(255) NULL,
    handler_id VARCHAR(36) NULL,
    handler_note VARCHAR(255) NULL,
    started_at DATETIME(3) NULL,
    completed_at DATETIME(3) NULL,
    cancelled_at DATETIME(3) NULL,
    terminated_reason VARCHAR(255) NULL,
    idempotency_key VARCHAR(64) NULL,
    version INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_boarding_no (booking_no),
    UNIQUE KEY uk_boarding_idem (user_id, idempotency_key),
    KEY idx_boarding_user_status (user_id, status),
    KEY idx_boarding_room_date (room_id, start_date, end_date),
    KEY idx_boarding_pet (pet_id),
    KEY idx_boarding_handler (handler_id),
    CONSTRAINT fk_boarding_user FOREIGN KEY (user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_boarding_pet FOREIGN KEY (pet_id) REFERENCES pet (id),
    CONSTRAINT fk_boarding_room FOREIGN KEY (room_id) REFERENCES boarding_room (id),
    CONSTRAINT chk_boarding_date_order CHECK (end_date > start_date),
    CONSTRAINT chk_boarding_quoted_non_negative CHECK (quoted_amount IS NULL OR quoted_amount >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Boarding booking with status machine and idempotency';

CREATE TABLE boarding_care_profile (
    id VARCHAR(36) NOT NULL,
    booking_id VARCHAR(36) NOT NULL,
    vaccination_summary VARCHAR(500) NULL,
    behavior_notes VARCHAR(500) NULL,
    feeding_plan VARCHAR(500) NULL,
    medication_plan VARCHAR(500) NULL,
    emergency_contact_ciphertext VARCHAR(255) NULL,
    emergency_authorization VARCHAR(32) NULL,
    access_scope VARCHAR(32) NOT NULL DEFAULT 'HANDLER',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_care_booking (booking_id),
    CONSTRAINT fk_care_booking FOREIGN KEY (booking_id) REFERENCES boarding_booking (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Care profile attached to a boarding booking; sensitive fields restricted to fulfillment role';

-- 权限 seed：boarding:manage 后台履约/分配/流转；room:read 房间占用查询；
-- room:manage 房间 CRUD；boarding:fulfill 履约角色可查照护敏感字段与发起履约。
INSERT IGNORE INTO sys_permission (id, code, resource, action, description)
VALUES
    ('00000000-0000-0000-0000-000000000226', 'boarding:manage', 'boarding', 'manage', 'Manage and fulfill boarding bookings'),
    ('00000000-0000-0000-0000-000000000227', 'room:read', 'room', 'read', 'Read boarding rooms and occupancy'),
    ('00000000-0000-0000-0000-000000000228', 'room:manage', 'room', 'manage', 'Create and update boarding rooms'),
    ('00000000-0000-0000-0000-000000000229', 'boarding:fulfill', 'boarding', 'fulfill', 'Fulfill boarding bookings and view care sensitive fields');

-- ADMIN (...102) 显式绑定本迁移引入的四个权限（V009 的 blanket SELECT 只覆盖其执行时已存在的权限）。
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000102', id FROM sys_permission
WHERE code IN ('boarding:manage', 'room:read', 'room:manage', 'boarding:fulfill');

-- SERVICE (...104) 履约角色：room:read（看房间占用）、boarding:fulfill（履约+看照护敏感字段）。
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000104', id FROM sys_permission
WHERE code IN ('room:read', 'boarding:fulfill');
