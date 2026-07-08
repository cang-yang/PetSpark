-- PR-AUTH-02: refresh-token rotation/revocation and password-reset verification codes.

CREATE TABLE auth_refresh_token (
    id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    token_hash CHAR(64) NOT NULL,
    family_id VARCHAR(36) NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    revoked_at DATETIME(3) NULL,
    replaced_by_id VARCHAR(36) NULL,
    client_fingerprint VARCHAR(128) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_refresh_hash (token_hash),
    KEY idx_refresh_user (user_id, revoked_at),
    KEY idx_refresh_family (family_id, revoked_at),
    CONSTRAINT fk_refresh_user FOREIGN KEY (user_id) REFERENCES sys_user (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Rotating refresh tokens; raw tokens are never stored';

CREATE TABLE auth_verification_code (
    id VARCHAR(36) NOT NULL,
    purpose VARCHAR(32) NOT NULL,
    principal VARCHAR(128) NOT NULL,
    code_hash VARCHAR(100) NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    consumed_at DATETIME(3) NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_code_lookup (purpose, principal, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='One-time verification codes';
