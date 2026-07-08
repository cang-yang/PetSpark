-- PR-SYSTEM-01: dictionaries, non-sensitive runtime config, and audit query.

CREATE TABLE sys_dict_type (
    id VARCHAR(36) NOT NULL,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    built_in TINYINT(1) NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_dict_type_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Dictionary types';

CREATE TABLE sys_dict_item (
    id VARCHAR(36) NOT NULL,
    type_code VARCHAR(64) NOT NULL,
    item_key VARCHAR(64) NOT NULL,
    item_label VARCHAR(128) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    version INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_dict_item_key (type_code, item_key),
    KEY idx_dict_item_type (type_code, sort_order),
    CONSTRAINT fk_dict_item_type FOREIGN KEY (type_code) REFERENCES sys_dict_type (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Dictionary items';

CREATE TABLE system_config (
    id VARCHAR(36) NOT NULL,
    config_key VARCHAR(128) NOT NULL,
    config_value VARCHAR(512) NOT NULL,
    value_type VARCHAR(16) NOT NULL DEFAULT 'STRING',
    description VARCHAR(255) NULL,
    protected_key TINYINT(1) NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_system_config_key (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Non-sensitive runtime configuration';

INSERT IGNORE INTO sys_permission (id, code, resource, action, description)
VALUES
    ('00000000-0000-0000-0000-000000000213', 'dict:read', 'dict', 'read', 'Read dictionary data'),
    ('00000000-0000-0000-0000-000000000214', 'dict:update', 'dict', 'update', 'Create and update dictionary data'),
    ('00000000-0000-0000-0000-000000000215', 'config:read', 'config', 'read', 'Read non-sensitive configuration'),
    ('00000000-0000-0000-0000-000000000216', 'config:update', 'config', 'update', 'Update non-sensitive configuration'),
    ('00000000-0000-0000-0000-000000000217', 'audit:read', 'audit', 'read', 'Read sanitized audit logs');

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000102', id FROM sys_permission;

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
VALUES ('00000000-0000-0000-0000-000000000105', '00000000-0000-0000-0000-000000000217');

INSERT IGNORE INTO sys_dict_type (id, code, name, built_in, status)
VALUES ('00000000-0000-0000-0000-000000000301', 'pet_gender', 'Pet Gender', 1, 'ACTIVE');

INSERT IGNORE INTO sys_dict_item (id, type_code, item_key, item_label, sort_order, status)
VALUES
    ('00000000-0000-0000-0000-000000000302', 'pet_gender', 'MALE', 'Male', 10, 'ACTIVE'),
    ('00000000-0000-0000-0000-000000000303', 'pet_gender', 'FEMALE', 'Female', 20, 'ACTIVE'),
    ('00000000-0000-0000-0000-000000000304', 'pet_gender', 'UNKNOWN', 'Unknown', 30, 'ACTIVE');

INSERT IGNORE INTO system_config (id, config_key, config_value, value_type, description, protected_key)
VALUES
    ('00000000-0000-0000-0000-000000000305', 'site.notice', '', 'STRING', 'Homepage public notice', 0),
    ('00000000-0000-0000-0000-000000000306', 'feature.registration.enabled', 'true', 'BOOLEAN', 'Whether public registration is enabled', 0);
