-- PR-AI-02: AI consent, conversations, messages, and call records.

CREATE TABLE ai_consent (
    id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    policy_version VARCHAR(32) NOT NULL,
    scopes VARCHAR(255) NOT NULL,
    granted_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    withdrawn_at DATETIME(3) NULL,
    PRIMARY KEY (id),
    KEY idx_ai_consent_user (user_id, withdrawn_at),
    CONSTRAINT fk_ai_consent_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI service consent log';

CREATE TABLE ai_conversation (
    id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    scene VARCHAR(32) NOT NULL,
    pet_id VARCHAR(36) NULL,
    title VARCHAR(120) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    expires_at DATETIME(3) NULL,
    deleted_at DATETIME(3) NULL,
    version INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_ai_conv_user (user_id, updated_at),
    CONSTRAINT fk_ai_conv_user FOREIGN KEY (user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_ai_conv_pet FOREIGN KEY (pet_id) REFERENCES pet (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI conversation session';

CREATE TABLE ai_message (
    id VARCHAR(36) NOT NULL,
    conversation_id VARCHAR(36) NOT NULL,
    role VARCHAR(16) NOT NULL,
    content_ciphertext TEXT NOT NULL,
    safety_label VARCHAR(32) NULL,
    token_count INT NOT NULL DEFAULT 0,
    deleted_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_ai_message_conv (conversation_id, created_at),
    CONSTRAINT fk_ai_message_conv FOREIGN KEY (conversation_id) REFERENCES ai_conversation (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI message with encrypted content';

CREATE TABLE ai_call_record (
    id VARCHAR(36) NOT NULL,
    request_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    scene VARCHAR(32) NOT NULL,
    provider VARCHAR(64) NOT NULL,
    model VARCHAR(64) NOT NULL,
    input_hash CHAR(64) NOT NULL,
    outcome VARCHAR(16) NOT NULL,
    error_code VARCHAR(64) NULL,
    prompt_tokens INT NOT NULL DEFAULT 0,
    completion_tokens INT NOT NULL DEFAULT 0,
    latency_ms INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_call_request (request_id),
    KEY idx_ai_call_user_time (user_id, created_at),
    CONSTRAINT fk_ai_call_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI call record without prompt content';
