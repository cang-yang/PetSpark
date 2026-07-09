-- PR-MEDICAL-01: 医疗服务视图最小扩展。
-- 复用 V022 service_item/service_resource/service_slot/service_booking 通用服务表与状态机，
-- 医疗以 service_item.kind = 'MEDICAL' 区分；本迁移新增医疗规则字段、权限与空库演示种子。
-- 通知继续由 NotificationService.send 通过 scene + event 键写 outbox，禁止写 notification_type 静态行。

CREATE TABLE service_medical_profile (
    id VARCHAR(36) NOT NULL,
    service_item_id VARCHAR(36) NOT NULL,
    clinic_license VARCHAR(255) NULL,
    veterinarian_team VARCHAR(500) NULL,
    supported_pet_types VARCHAR(255) NULL,
    care_scope VARCHAR(500) NULL,
    appointment_notice VARCHAR(500) NULL,
    emergency_rule VARCHAR(500) NULL,
    active TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_medical_profile_item (service_item_id),
    CONSTRAINT fk_medical_profile_item FOREIGN KEY (service_item_id) REFERENCES service_item (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Medical service rules for service_item kind=MEDICAL';

INSERT IGNORE INTO sys_permission (id, code, resource, action, description)
VALUES
    ('00000000-0000-0000-0000-000000000236', 'medical:manage', 'medical', 'manage', 'Manage medical service items and bookings'),
    ('00000000-0000-0000-0000-000000000237', 'medical:fulfill', 'medical', 'fulfill', 'Fulfill medical service bookings');

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000102', id FROM sys_permission
WHERE code IN ('medical:manage', 'medical:fulfill');

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000104', id FROM sys_permission
WHERE code IN ('medical:manage', 'medical:fulfill');

-- 可用于空库演示/前端首屏的医疗服务种子，保持 service_item.kind='MEDICAL'。
INSERT IGNORE INTO service_item
    (id, kind, code, name, description, qualification, availability_note, exception_rule, base_price, status, version)
VALUES
    ('00000000-0000-0000-0000-000000000261', 'MEDICAL', 'MEDICAL-CHECKUP', '基础健康体检',
     '基础问诊、体格检查、体温/体重记录与健康建议。', '执业兽医师接诊；医疗机构执业许可已公示',
     '每日 09:30-17:30，需提前预约并携带免疫记录', '急症、传染病疑似或严重创伤请优先急诊，不适合普通预约', 99.00, 'ACTIVE', 0),
    ('00000000-0000-0000-0000-000000000262', 'MEDICAL', 'MEDICAL-VACCINE', '疫苗接种咨询',
     '疫苗接种前评估、接种方案建议与接种后观察说明。', '执业兽医师评估；冷链疫苗按规范管理',
     '工作日 10:00-16:00，幼宠首次免疫请预留更长时段', '发热、腹泻、精神沉郁或近期应激时建议延期接种', 59.00, 'ACTIVE', 0);

INSERT IGNORE INTO service_specification
    (id, service_item_id, name, price_delta, sort_order, status)
VALUES
    ('00000000-0000-0000-0000-000000000263', '00000000-0000-0000-0000-000000000261', '基础体检', 0.00, 1, 'ACTIVE'),
    ('00000000-0000-0000-0000-000000000264', '00000000-0000-0000-0000-000000000261', '体检 + 血常规建议', 80.00, 2, 'ACTIVE'),
    ('00000000-0000-0000-0000-000000000265', '00000000-0000-0000-0000-000000000262', '接种前评估', 0.00, 1, 'ACTIVE');

INSERT IGNORE INTO service_medical_profile
    (id, service_item_id, clinic_license, veterinarian_team, supported_pet_types, care_scope, appointment_notice, emergency_rule)
VALUES
    ('00000000-0000-0000-0000-000000000266', '00000000-0000-0000-0000-000000000261',
     '动物诊疗许可证：PETSPARK-MED-001', '内科/全科执业兽医团队，擅长犬猫常规健康评估', 'DOG,CAT',
     '健康体检、基础问诊、免疫记录核对、健康建议', '预约前请整理既往病史、免疫记录与近期用药信息',
     '呼吸困难、抽搐、持续呕吐腹泻等急症请直接急诊'),
    ('00000000-0000-0000-0000-000000000267', '00000000-0000-0000-0000-000000000262',
     '动物诊疗许可证：PETSPARK-MED-001', '免疫门诊兽医师团队，负责接种前评估与观察建议', 'DOG,CAT',
     '接种前健康评估、免疫程序咨询、接种后注意事项', '到店请携带疫苗本，接种后建议留观 20 分钟',
     '已出现过敏反应或当前健康异常需先由兽医评估');

INSERT IGNORE INTO service_resource
    (id, service_item_id, name, qualification, availability_note, exception_rule, status, capacity, version)
VALUES
    ('00000000-0000-0000-0000-000000000268', '00000000-0000-0000-0000-000000000261', '全科诊室 A / 王医生',
     '执业兽医师，犬猫全科门诊经验 8 年', '每窗口 1 组宠物，需按预约时段到店', '疑似传染病请先电话沟通并按隔离流程就诊', 'ACTIVE', 1, 0),
    ('00000000-0000-0000-0000-000000000269', '00000000-0000-0000-0000-000000000262', '免疫门诊 B / 李医生',
     '执业兽医师，负责犬猫免疫接种评估', '每窗口最多 2 组，接种后需留观', '健康异常或免疫禁忌时可改期或转诊', 'ACTIVE', 2, 0);

INSERT IGNORE INTO service_slot
    (id, resource_id, slot_date, start_at, end_at, capacity, booked_count, status, version)
VALUES
    ('00000000-0000-0000-0000-000000000270', '00000000-0000-0000-0000-000000000268', '2026-07-14', '2026-07-14 01:30:00.000', '2026-07-14 02:00:00.000', 1, 0, 'OPEN', 0),
    ('00000000-0000-0000-0000-000000000271', '00000000-0000-0000-0000-000000000268', '2026-07-15', '2026-07-15 06:00:00.000', '2026-07-15 06:30:00.000', 1, 0, 'OPEN', 0),
    ('00000000-0000-0000-0000-000000000272', '00000000-0000-0000-0000-000000000269', '2026-07-16', '2026-07-16 02:00:00.000', '2026-07-16 02:30:00.000', 2, 0, 'OPEN', 0);
