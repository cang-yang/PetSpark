/**
 * 寄养路由模块。发起寄养预约、我的寄养预约、后台房间资源管理、后台寄养预约履约。
 * 后续新增寄养类页面只在本文件追加路由，不修改 router/index.js。
 */
export default [
  { path: '/boarding/new', name: 'boarding-new', component: () => import('../../views/BoardingNewView.vue') },
  { path: '/my/boarding', name: 'my-boarding', component: () => import('../../views/MyBoardingsView.vue') },
  { path: '/admin/rooms', name: 'admin-rooms', component: () => import('../../views/AdminRoomsView.vue') },
  { path: '/admin/boarding', name: 'admin-boarding', component: () => import('../../views/AdminBoardingsView.vue') }
]
