-- PR-FILE-01: secure staged image metadata and upload permission.

CREATE TABLE file_object (
    id VARCHAR(36) NOT NULL,
    object_key VARCHAR(128) NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    media_type VARCHAR(64) NOT NULL,
    extension VARCHAR(16) NOT NULL,
    size_bytes BIGINT NOT NULL,
    sha256 CHAR(64) NOT NULL,
    width INT NULL,
    height INT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'STAGED',
    owner_id VARCHAR(36) NOT NULL,
    business_type VARCHAR(64) NOT NULL,
    confirmed_at DATETIME(3) NULL,
    deleted_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_file_key (object_key),
    KEY idx_file_owner (owner_id, business_type),
    KEY idx_file_status_created (status, created_at),
    CONSTRAINT fk_file_owner FOREIGN KEY (owner_id) REFERENCES sys_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Stored file metadata';

INSERT INTO sys_permission (id, code, resource, action, description)
VALUES ('00000000-0000-0000-0000-000000000202', 'file:upload', 'file', 'upload', 'Upload images');

INSERT INTO sys_role_permission (role_id, permission_id)
VALUES ('00000000-0000-0000-0000-000000000101', '00000000-0000-0000-0000-000000000202');
