/**
 * 美容服务路由模块。只注册 BEAUTY 视图，底层复用 service API 与通用预约状态机。
 */
export default [
  { path: '/beauty', name: 'beauty', component: () => import('../../views/BeautyListView.vue') },
  { path: '/beauty/:id', name: 'beauty-detail', component: () => import('../../views/BeautyDetailView.vue') },
  { path: '/my/beauty/bookings', name: 'my-beauty-bookings', component: () => import('../../views/MyBeautyBookingsView.vue') },
  { path: '/admin/beauty', name: 'admin-beauty', component: () => import('../../views/AdminBeautyView.vue') }
]
