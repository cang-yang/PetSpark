/**
 * 训练服务路由模块。训练以 service_item.kind=TRAINING 复用通用服务预约状态机。
 */
export default [
  { path: '/training', name: 'training', component: () => import('../../views/TrainingServiceListView.vue') },
  { path: '/training/:id', name: 'training-detail', component: () => import('../../views/TrainingServiceDetailView.vue') },
  { path: '/my/training/bookings', name: 'my-training-bookings', component: () => import('../../views/MyTrainingBookingsView.vue') },
  { path: '/admin/training', name: 'admin-training', component: () => import('../../views/AdminTrainingView.vue') }
]
