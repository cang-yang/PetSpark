/**
 * 医疗服务路由模块。复用通用服务预约底座，以 kind=MEDICAL 提供独立视图入口。
 */
export default [
  { path: '/medical', name: 'medical', component: () => import('../../views/MedicalListView.vue') },
  { path: '/medical/:id', name: 'medical-detail', component: () => import('../../views/MedicalDetailView.vue') },
  { path: '/my/medical/bookings', name: 'my-medical-bookings', component: () => import('../../views/MyMedicalBookingsView.vue') },
  { path: '/admin/medical', name: 'admin-medical', component: () => import('../../views/AdminMedicalView.vue') }
]
