-- PR-HEALTH-01: pet health records with encrypted detail, revision chain, and privacy erase.

CREATE TABLE pet_health_record (
    id VARCHAR(36) NOT NULL,
    pet_id VARCHAR(36) NOT NULL,
    record_type VARCHAR(32) NOT NULL,
    occurred_on DATE NOT NULL,
    summary VARCHAR(200) NOT NULL,
    detail_ciphertext TEXT NULL,
    attachment_file_id VARCHAR(36) NULL,
    source_role VARCHAR(32) NOT NULL,
    author_id VARCHAR(36) NOT NULL,
    revision_of_id VARCHAR(36) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    privacy_erased_at DATETIME(3) NULL,
    erase_reason VARCHAR(200) NULL,
    erased_by VARCHAR(36) NULL,
    version INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_health_pet_time (pet_id, occurred_on),
    KEY idx_health_author (author_id),
    KEY idx_health_revision (revision_of_id),
    CONSTRAINT fk_health_pet FOREIGN KEY (pet_id) REFERENCES pet (id),
    CONSTRAINT fk_health_author FOREIGN KEY (author_id) REFERENCES sys_user (id),
    CONSTRAINT fk_health_revision FOREIGN KEY (revision_of_id) REFERENCES pet_health_record (id),
    CONSTRAINT fk_health_attachment FOREIGN KEY (attachment_file_id) REFERENCES file_object (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Pet health record with revision chain';

-- health:manage = create/revise own or in-scope health records;
-- health:correct = revise any pet health record (correction authority);
-- privacy:manage = legally erase sensitive health content (ADMIN).
INSERT IGNORE INTO sys_permission (id, code, resource, action, description)
VALUES
    ('00000000-0000-0000-0000-000000000222', 'health:manage', 'health', 'manage', 'Create and revise pet health records'),
    ('00000000-0000-0000-0000-000000000223', 'health:correct', 'health', 'correct', 'Revise any pet health record'),
    ('00000000-0000-0000-0000-000000000224', 'privacy:manage', 'privacy', 'manage', 'Legally erase sensitive health content');

-- SERVICE role (...104) gets health:manage so fulfillment staff can add records in scope.
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000104', id FROM sys_permission WHERE code = 'health:manage';

-- ADMIN role (...102) explicitly bound to the new health/privacy perms.
-- V009's blanket SELECT only captured perms existing at V009 execution time, so
-- perms introduced by later migrations must be bound here to reach ADMIN roles.
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000102', id FROM sys_permission
WHERE code IN ('health:manage', 'health:correct', 'privacy:manage');
