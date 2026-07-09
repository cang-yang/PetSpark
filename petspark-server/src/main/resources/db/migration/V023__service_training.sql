-- PR-TRAINING-01: 训练项目与申请视图。
-- 复用 V022 service_item/service_resource/service_slot/service_booking 与状态机，
-- 训练以 service_item.kind = 'TRAINING' 区分；本迁移只新增训练申请最小扩展表与示例种子。
-- 通知继续由 NotificationService.send 通过 scene+event 键写 outbox，禁止写 notification_type 静态行。

CREATE TABLE training_application_detail (
    id VARCHAR(36) NOT NULL,
    booking_id VARCHAR(36) NOT NULL,
    training_goal VARCHAR(500) NOT NULL,
    behavior_problem VARCHAR(500) NULL,
    intensity VARCHAR(16) NOT NULL DEFAULT 'MEDIUM',
    attention_note VARCHAR(500) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_training_detail_booking (booking_id),
    CONSTRAINT fk_training_detail_booking FOREIGN KEY (booking_id) REFERENCES service_booking (id),
    CONSTRAINT chk_training_detail_intensity CHECK (intensity IN ('LOW','MEDIUM','HIGH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Training-only application detail linked to service_booking';

-- 示例训练项目/资源/窗口，便于空库 V001→V023 后直接浏览训练视图；不影响后台继续维护。
INSERT IGNORE INTO service_item
    (id, kind, code, name, description, qualification, availability_note, exception_rule, base_price, status, version)
VALUES
    ('00000000-0000-0000-0000-000000000623', 'TRAINING', 'TRAINING-BASIC', '基础行为训练',
     '面向犬猫的基础服从、社会化与家庭行为纠偏训练。', '持证宠物行为训练师；训练前评估宠物健康与应激情况。',
     '每周二/四/六 10:00-18:00，可在详情页选择开放窗口。', '攻击风险、严重疾病或应激过高时可改期或终止训练。',
     199.00, 'ACTIVE', 0),
    ('00000000-0000-0000-0000-000000000624', 'TRAINING', 'TRAINING-ADVANCED', '进阶行为矫正',
     '针对扑人、护食、分离焦虑等行为问题的进阶训练申请。', '高级行为训练师；需填写行为问题与注意事项。',
     '按训练师档期开放预约窗口。', '存在咬伤历史需提前备注，现场评估后确认是否履约。',
     399.00, 'ACTIVE', 0);

INSERT IGNORE INTO service_specification
    (id, service_item_id, name, price_delta, sort_order, status)
VALUES
    ('00000000-0000-0000-0000-000000000625', '00000000-0000-0000-0000-000000000623', '单次体验课', 0.00, 1, 'ACTIVE'),
    ('00000000-0000-0000-0000-000000000626', '00000000-0000-0000-0000-000000000623', '三次巩固课', 300.00, 2, 'ACTIVE'),
    ('00000000-0000-0000-0000-000000000627', '00000000-0000-0000-0000-000000000624', '一对一评估课', 0.00, 1, 'ACTIVE');

INSERT IGNORE INTO service_resource
    (id, service_item_id, name, qualification, availability_note, exception_rule, status, capacity, version)
VALUES
    ('00000000-0000-0000-0000-000000000628', '00000000-0000-0000-0000-000000000623', '训练师 A / 行为训练室',
     'CPDT-KA 训练师，擅长基础服从与社会化。', '小班或一对一训练，每窗口最多 2 组。', '宠物明显不适时暂停训练。', 'ACTIVE', 2, 0),
    ('00000000-0000-0000-0000-000000000629', '00000000-0000-0000-0000-000000000624', '训练师 B / 矫正评估室',
     '高级行为顾问，擅长焦虑与护食矫正。', '高强度训练一对一进行。', '攻击风险需佩戴牵引与防护用品。', 'ACTIVE', 1, 0);

INSERT IGNORE INTO service_slot
    (id, resource_id, slot_date, start_at, end_at, capacity, booked_count, status, version)
VALUES
    ('00000000-0000-0000-0000-000000000630', '00000000-0000-0000-0000-000000000628', '2026-07-14', '2026-07-14 02:00:00.000', '2026-07-14 03:00:00.000', 2, 0, 'OPEN', 0),
    ('00000000-0000-0000-0000-000000000631', '00000000-0000-0000-0000-000000000628', '2026-07-16', '2026-07-16 06:00:00.000', '2026-07-16 07:00:00.000', 2, 0, 'OPEN', 0),
    ('00000000-0000-0000-0000-000000000632', '00000000-0000-0000-0000-000000000629', '2026-07-15', '2026-07-15 03:00:00.000', '2026-07-15 04:30:00.000', 1, 0, 'OPEN', 0);
