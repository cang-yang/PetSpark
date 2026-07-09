/**
 * 服务预约路由模块。服务列表/详情、我的服务预约、后台服务管理入口。
 * 后续新增服务类页面只在本文件追加路由，不修改 router/index.js。
 */
export default [
  { path: '/services', name: 'services', component: () => import('../../views/ServiceListView.vue') },
  { path: '/services/:id', name: 'service-detail', component: () => import('../../views/ServiceItemDetailView.vue') },
  { path: '/my/services/bookings', name: 'my-service-bookings', component: () => import('../../views/MyServiceBookingsView.vue') },
  { path: '/admin/services', name: 'admin-services', component: () => import('../../views/AdminServiceView.vue') }
]
