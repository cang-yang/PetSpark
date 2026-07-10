-- PR-DASHBOARD-01: 后台统计仪表盘只读权限。
-- 仅登记权限码并绑定到 ADMIN 角色（.102），不创建业务表——仪表盘只读取既有模块表。
-- UUID 分配顺序接 V028(.250)，按可用槽位使用 .290。

INSERT IGNORE INTO sys_permission (id, code, resource, action, description)
VALUES ('00000000-0000-0000-0000-000000000290', 'dashboard:read', 'dashboard', 'read',
        'Read admin dashboard aggregate metrics');

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
VALUES ('00000000-0000-0000-0000-000000000102', '00000000-0000-0000-0000-000000000290');
