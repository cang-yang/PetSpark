-- PR-BEAUTY-01: 美容服务视图最小扩展。
-- 复用 V022 service_item/service_resource/service_slot/service_booking 通用服务表与状态机，
-- 仅为 kind=BEAUTY 的服务项目补充美容规则字段；通知继续走 scene + event，不插 notification_type。

CREATE TABLE service_beauty_profile (
    id VARCHAR(36) NOT NULL,
    service_item_id VARCHAR(36) NOT NULL,
    supported_pet_types VARCHAR(255) NULL,
    coat_types VARCHAR(255) NULL,
    size_ranges VARCHAR(255) NULL,
    care_preferences VARCHAR(500) NULL,
    caution_notes VARCHAR(500) NULL,
    active TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_beauty_profile_item (service_item_id),
    CONSTRAINT fk_beauty_profile_item FOREIGN KEY (service_item_id) REFERENCES service_item (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Beauty service rules for service_item kind=BEAUTY';

INSERT IGNORE INTO sys_permission (id, code, resource, action, description)
VALUES
    ('00000000-0000-0000-0000-000000000234', 'beauty:manage', 'beauty', 'manage', 'Manage beauty service items and bookings'),
    ('00000000-0000-0000-0000-000000000235', 'beauty:fulfill', 'beauty', 'fulfill', 'Fulfill beauty service bookings');

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000102', id FROM sys_permission
WHERE code IN ('beauty:manage', 'beauty:fulfill');

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000104', id FROM sys_permission
WHERE code IN ('beauty:manage', 'beauty:fulfill');

-- 可用于空库演示/前端首屏的美容服务种子，保持 service_item.kind='BEAUTY'。
INSERT IGNORE INTO service_item
    (id, kind, code, name, description, qualification, availability_note, exception_rule, base_price, status, version)
VALUES
    ('00000000-0000-0000-0000-000000000241', 'BEAUTY', 'BEAUTY-BASIC-GROOM', '基础美容护理',
     '洗护、吹干、基础梳理与耳爪清洁。', '持证宠物美容师；工具一宠一消毒',
     '每日 10:00-18:00，需提前预约', '严重皮肤病、应激明显或疫苗异常时暂停服务', 128.00, 'ACTIVE', 0),
    ('00000000-0000-0000-0000-000000000242', 'BEAUTY', 'BEAUTY-STYLING', '造型修剪',
     '按体型与毛发状态进行精修造型。', '高级宠物美容师；服务前评估毛结与皮肤状态',
     '工作日 11:00-17:00，大型犬请预留更长时段', '重度毛结需现场确认，可能调整方案或建议剃短', 198.00, 'ACTIVE', 0);

INSERT IGNORE INTO service_beauty_profile
    (id, service_item_id, supported_pet_types, coat_types, size_ranges, care_preferences, caution_notes)
VALUES
    ('00000000-0000-0000-0000-000000000251', '00000000-0000-0000-0000-000000000241',
     'DOG,CAT', 'SHORT,LONG,CURLY', 'SMALL,MEDIUM', '温和洗护、低噪吹干、基础梳毛',
     '到店请说明过敏史、皮肤病史与近期驱虫/疫苗情况'),
    ('00000000-0000-0000-0000-000000000252', '00000000-0000-0000-0000-000000000242',
     'DOG', 'LONG,CURLY,DOUBLE', 'SMALL,MEDIUM,LARGE', '造型沟通、毛结评估、局部精修',
     '攻击性、重度应激或严重毛结需先由美容师评估');
