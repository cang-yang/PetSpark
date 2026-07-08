-- PR-RBAC-01: user administration, roles, and permission management.

INSERT IGNORE INTO sys_role (id, code, name, built_in, status)
VALUES
    ('00000000-0000-0000-0000-000000000102', 'ADMIN', 'Platform Administrator', 1, 'ACTIVE'),
    ('00000000-0000-0000-0000-000000000103', 'OP', 'Operations Staff', 1, 'ACTIVE'),
    ('00000000-0000-0000-0000-000000000104', 'SERVICE', 'Service Staff', 1, 'ACTIVE'),
    ('00000000-0000-0000-0000-000000000105', 'AUDITOR', 'Audit Staff', 1, 'ACTIVE');

INSERT IGNORE INTO sys_permission (id, code, resource, action, description)
VALUES
    ('00000000-0000-0000-0000-000000000209', 'user:read', 'user', 'read', 'Read backend user list and details'),
    ('00000000-0000-0000-0000-000000000210', 'user:update', 'user', 'update', 'Update user status and role assignments'),
    ('00000000-0000-0000-0000-000000000211', 'role:read', 'role', 'read', 'Read roles and permissions'),
    ('00000000-0000-0000-0000-000000000212', 'role:update', 'role', 'update', 'Create roles and update custom role permissions');

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000102', id FROM sys_permission;
