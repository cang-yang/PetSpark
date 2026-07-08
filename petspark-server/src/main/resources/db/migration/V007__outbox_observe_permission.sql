-- PR-NOTIFY-01: 运营观测权限 system:observe，供 OutboxAdminController 读取 outbox 状态计数。
-- 只登记权限码，不绑定到任何内置角色——默认 USER 角色无此权限（最小权限），
-- PR-RBAC-01 引入 ADMIN 角色后再绑定，使该端点对运营/运维可见。

INSERT INTO sys_permission (id, code, resource, action, description)
VALUES ('00000000-0000-0000-0000-000000000203', 'system:observe', 'system', 'observe',
        'View outbox backlog and dead-letter counters');
