-- PR-PET-01: correct permission ids that collided with PR-SYSTEM-01.

INSERT IGNORE INTO sys_permission (id, code, resource, action, description)
VALUES
    ('00000000-0000-0000-0000-000000000219', 'pet:manage', 'pet', 'manage', 'Manage pet catalog records'),
    ('00000000-0000-0000-0000-000000000220', 'pet:status', 'pet', 'status', 'Change pet lifecycle status'),
    ('00000000-0000-0000-0000-000000000221', 'breed:manage', 'breed', 'manage', 'Manage pet breeds');

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000102', id
FROM sys_permission
WHERE code IN ('pet:manage', 'pet:status', 'breed:manage');
