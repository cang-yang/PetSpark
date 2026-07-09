-- PR-COMMUNITY-01: community posts, comments, interactions, and moderation.
-- Permission UUID collision check: existing migrations use 000000000237 and 000000000241+ for service seed data;
-- 000000000238/239 are unused in V001-V025 and reserved here for community permissions.

CREATE TABLE community_post (
    id VARCHAR(36) NOT NULL,
    author_user_id VARCHAR(36) NOT NULL,
    title VARCHAR(120) NOT NULL,
    content TEXT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PUBLISHED',
    moderation_reason VARCHAR(255) NULL,
    moderated_by VARCHAR(36) NULL,
    moderated_at DATETIME(3) NULL,
    like_count INT NOT NULL DEFAULT 0,
    favorite_count INT NOT NULL DEFAULT 0,
    comment_count INT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    deleted_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_community_post_status_created (status, created_at),
    KEY idx_community_post_author_created (author_user_id, created_at),
    CONSTRAINT fk_community_post_author FOREIGN KEY (author_user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_community_post_moderator FOREIGN KEY (moderated_by) REFERENCES sys_user (id),
    CONSTRAINT chk_community_post_status CHECK (status IN ('PUBLISHED','HIDDEN')),
    CONSTRAINT chk_community_post_counts CHECK (like_count >= 0 AND favorite_count >= 0 AND comment_count >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Community user posts';

CREATE TABLE community_comment (
    id VARCHAR(36) NOT NULL,
    post_id VARCHAR(36) NOT NULL,
    parent_id VARCHAR(36) NULL,
    author_user_id VARCHAR(36) NOT NULL,
    content VARCHAR(1000) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PUBLISHED',
    moderation_reason VARCHAR(255) NULL,
    moderated_by VARCHAR(36) NULL,
    moderated_at DATETIME(3) NULL,
    version INT NOT NULL DEFAULT 0,
    deleted_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_community_comment_post_created (post_id, created_at),
    KEY idx_community_comment_author_created (author_user_id, created_at),
    CONSTRAINT fk_community_comment_post FOREIGN KEY (post_id) REFERENCES community_post (id),
    CONSTRAINT fk_community_comment_parent FOREIGN KEY (parent_id) REFERENCES community_comment (id),
    CONSTRAINT fk_community_comment_author FOREIGN KEY (author_user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_community_comment_moderator FOREIGN KEY (moderated_by) REFERENCES sys_user (id),
    CONSTRAINT chk_community_comment_status CHECK (status IN ('PUBLISHED','HIDDEN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Community post comments';

CREATE TABLE community_interaction (
    id VARCHAR(36) NOT NULL,
    post_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    type VARCHAR(16) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_community_interaction_user_type (post_id, user_id, type),
    KEY idx_community_interaction_user (user_id, type, created_at),
    CONSTRAINT fk_community_interaction_post FOREIGN KEY (post_id) REFERENCES community_post (id),
    CONSTRAINT fk_community_interaction_user FOREIGN KEY (user_id) REFERENCES sys_user (id),
    CONSTRAINT chk_community_interaction_type CHECK (type IN ('LIKE','FAVORITE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Community post likes and favorites';

INSERT IGNORE INTO sys_permission (id, code, resource, action, description)
VALUES
    ('00000000-0000-0000-0000-000000000238', 'community:manage', 'community', 'manage', 'Manage community posts and comments'),
    ('00000000-0000-0000-0000-000000000239', 'community:moderate', 'community', 'moderate', 'Moderate community content status');

-- ADMIN (...102) gets full community management. OP (...103) can moderate content.
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000102', id FROM sys_permission
WHERE code IN ('community:manage', 'community:moderate');

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000103', id FROM sys_permission
WHERE code = 'community:moderate';
