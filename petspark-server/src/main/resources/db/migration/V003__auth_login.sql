-- PR-AUTH-01: captcha, user registration, login, and access-token authentication.

CREATE TABLE sys_user (
    id VARCHAR(36) NOT NULL COMMENT 'Application-generated UUID primary key',
    username VARCHAR(64) NOT NULL COMMENT 'Unique login username',
    email VARCHAR(128) NOT NULL COMMENT 'Unique email address',
    password_hash VARCHAR(100) NOT NULL COMMENT 'BCrypt password hash',
    nickname VARCHAR(64) NOT NULL COMMENT 'Display name',
    avatar_file_id VARCHAR(64) NULL COMMENT 'Avatar file reference',
    phone_ciphertext VARCHAR(255) NULL COMMENT 'Encrypted phone number, when collected',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE, DISABLED, LOCKED',
    token_version INT NOT NULL DEFAULT 0 COMMENT 'Increment to invalidate issued tokens',
    last_login_at DATETIME(3) NULL COMMENT 'Last successful login time',
    version INT NOT NULL DEFAULT 0 COMMENT 'Optimistic lock version',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_user_username (username),
    UNIQUE KEY uk_sys_user_email (email),
    KEY idx_sys_user_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='System users';

CREATE TABLE sys_role (
    id VARCHAR(36) NOT NULL COMMENT 'Application-generated UUID primary key',
    code VARCHAR(64) NOT NULL COMMENT 'Stable role code',
    name VARCHAR(64) NOT NULL COMMENT 'Role name',
    built_in TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'Whether role is seeded by system',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE or DISABLED',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_role_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Roles';

CREATE TABLE sys_permission (
    id VARCHAR(36) NOT NULL COMMENT 'Application-generated UUID primary key',
    code VARCHAR(128) NOT NULL COMMENT 'Permission code, for example pet:read',
    resource VARCHAR(64) NOT NULL COMMENT 'Protected resource',
    action VARCHAR(64) NOT NULL COMMENT 'Allowed action',
    description VARCHAR(255) NULL COMMENT 'Human-readable description',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_permission_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Permissions';

CREATE TABLE sys_user_role (
    user_id VARCHAR(36) NOT NULL,
    role_id VARCHAR(36) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (user_id, role_id),
    KEY idx_sys_user_role_role (role_id),
    CONSTRAINT fk_sys_user_role_user FOREIGN KEY (user_id) REFERENCES sys_user (id) ON DELETE CASCADE,
    CONSTRAINT fk_sys_user_role_role FOREIGN KEY (role_id) REFERENCES sys_role (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='User-role relation';

CREATE TABLE sys_role_permission (
    role_id VARCHAR(36) NOT NULL,
    permission_id VARCHAR(36) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (role_id, permission_id),
    KEY idx_sys_role_permission_permission (permission_id),
    CONSTRAINT fk_sys_role_permission_role FOREIGN KEY (role_id) REFERENCES sys_role (id) ON DELETE CASCADE,
    CONSTRAINT fk_sys_role_permission_permission FOREIGN KEY (permission_id) REFERENCES sys_permission (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Role-permission relation';

CREATE TABLE auth_captcha (
    id VARCHAR(36) NOT NULL COMMENT 'Application-generated UUID primary key',
    challenge_hash VARCHAR(128) NOT NULL COMMENT 'Hash of public challenge text for audit correlation',
    answer_hash VARCHAR(100) NOT NULL COMMENT 'BCrypt hash of the expected answer',
    client_hash VARCHAR(128) NOT NULL COMMENT 'Client fingerprint hash supplied by frontend',
    expires_at DATETIME(3) NOT NULL COMMENT 'Expiration time',
    consumed_at DATETIME(3) NULL COMMENT 'Set after successful verification',
    attempt_count INT NOT NULL DEFAULT 0 COMMENT 'Failed verification attempts',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_auth_captcha_client (client_hash),
    KEY idx_auth_captcha_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='One-time arithmetic captchas';

INSERT INTO sys_role (id, code, name, built_in, status)
VALUES ('00000000-0000-0000-0000-000000000101', 'USER', 'Registered User', 1, 'ACTIVE');

INSERT INTO sys_permission (id, code, resource, action, description)
VALUES ('00000000-0000-0000-0000-000000000201', 'pet:read', 'pet', 'read', 'Read pet resources');

INSERT INTO sys_role_permission (role_id, permission_id)
VALUES ('00000000-0000-0000-0000-000000000101', '00000000-0000-0000-0000-000000000201');
