-- PR-STRAY-01: stray animal clues and rescue handling.
-- Independent STRAY line: member clue submission + admin rescue status/assignment/notes.
-- Permission IDs checked against V001-V026: 000...240/243 are unused before this migration.

CREATE TABLE stray_clue (
    id VARCHAR(36) NOT NULL,
    clue_no VARCHAR(64) NOT NULL COMMENT '展示用线索编号',
    reporter_user_id VARCHAR(36) NOT NULL COMMENT '提交人',
    animal_type VARCHAR(16) NOT NULL COMMENT 'DOG/CAT/OTHER',
    location VARCHAR(255) NOT NULL COMMENT '发现位置',
    description VARCHAR(1000) NOT NULL COMMENT '现场描述',
    contact_phone VARCHAR(32) NULL COMMENT '联系手机号，用户自愿填写',
    status VARCHAR(24) NOT NULL DEFAULT 'SUBMITTED' COMMENT 'SUBMITTED/ASSIGNED/IN_RESCUE/RESOLVED/CLOSED',
    assigned_user_id VARCHAR(36) NULL COMMENT '救助负责人',
    admin_note VARCHAR(500) NULL COMMENT '后台处理备注',
    handoff_pet_id VARCHAR(36) NULL COMMENT '后续转宠物/领养占位引用，不强耦合 pet 表',
    handoff_note VARCHAR(500) NULL COMMENT '后续领养/宠物建档占位备注',
    idempotency_key VARCHAR(64) NULL COMMENT '提交人级别幂等键',
    version INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    closed_at DATETIME(3) NULL,
    deleted_at DATETIME(3) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_stray_clue_no (clue_no),
    UNIQUE KEY uk_stray_clue_idem (reporter_user_id, idempotency_key),
    KEY idx_stray_reporter (reporter_user_id, status),
    KEY idx_stray_status (status, created_at),
    KEY idx_stray_assignee (assigned_user_id),
    CONSTRAINT fk_stray_reporter FOREIGN KEY (reporter_user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_stray_assignee FOREIGN KEY (assigned_user_id) REFERENCES sys_user (id),
    CONSTRAINT chk_stray_animal_type CHECK (animal_type IN ('DOG','CAT','OTHER')),
    CONSTRAINT chk_stray_status CHECK (status IN ('SUBMITTED','ASSIGNED','IN_RESCUE','RESOLVED','CLOSED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流浪动物救助线索';

CREATE TABLE stray_clue_image (
    id VARCHAR(36) NOT NULL,
    clue_id VARCHAR(36) NOT NULL,
    file_id VARCHAR(36) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_stray_clue_file (clue_id, file_id),
    KEY idx_stray_image_file (file_id),
    CONSTRAINT fk_stray_image_clue FOREIGN KEY (clue_id) REFERENCES stray_clue (id) ON DELETE CASCADE,
    CONSTRAINT fk_stray_image_file FOREIGN KEY (file_id) REFERENCES file_object (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流浪线索图片';

INSERT IGNORE INTO sys_permission (id, code, resource, action, description)
VALUES
    ('00000000-0000-0000-0000-000000000240', 'stray:read', 'stray', 'read', 'Read stray rescue clues'),
    ('00000000-0000-0000-0000-000000000243', 'stray:manage', 'stray', 'manage', 'Assign and transition stray rescue clues');

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000102', id FROM sys_permission
WHERE code IN ('stray:read', 'stray:manage');

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000103', id FROM sys_permission
WHERE code IN ('stray:read', 'stray:manage');
