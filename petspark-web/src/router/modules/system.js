/**
 * 系统管理路由模块。用户管理与系统管理（RBAC/系统后台）入口。
 */
export default [
  { path: '/admin/users', name: 'admin-users', component: () => import('../../views/AdminUsersView.vue') },
  { path: '/admin/system', name: 'admin-system', component: () => import('../../views/SystemAdminView.vue') }
]
