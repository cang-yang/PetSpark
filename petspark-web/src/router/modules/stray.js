/** 流浪救助路由模块。会员提交/查看线索，后台受理救助闭环。 */
export default [
  { path: '/stray', name: 'stray-clues', component: () => import('../../views/StrayCluesView.vue') },
  { path: '/my/stray-clues', name: 'my-stray-clues', component: () => import('../../views/MyStrayCluesView.vue') },
  { path: '/admin/stray-clues', name: 'admin-stray-clues', component: () => import('../../views/AdminStrayCluesView.vue') }
]
