/**
 * 领养路由模块。可领养宠物浏览、本人申请与状态、管理员审核与交接。
 */
export default [
  { path: '/adoptions', name: 'adoptions', component: () => import('../../views/AdoptablePetsView.vue') },
  { path: '/my/adoptions', name: 'my-adoptions', component: () => import('../../views/MyAdoptionsView.vue') },
  { path: '/admin/adoptions', name: 'admin-adoptions', component: () => import('../../views/AdminAdoptionsView.vue') }
]
