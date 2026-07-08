-- PR-USER-01: self-service user profile fields.

ALTER TABLE sys_user
    ADD COLUMN profile_bio VARCHAR(255) NULL COMMENT 'User profile introduction, visible according to profile API rules' AFTER phone_ciphertext;

INSERT INTO sys_permission (id, code, resource, action, description)
VALUES ('00000000-0000-0000-0000-000000000208', 'user:profile', 'user', 'profile', 'Maintain own user profile')
ON DUPLICATE KEY UPDATE description = VALUES(description);

INSERT INTO sys_role_permission (role_id, permission_id)
VALUES ('00000000-0000-0000-0000-000000000101', '00000000-0000-0000-0000-000000000208')
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);
