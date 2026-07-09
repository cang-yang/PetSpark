/**
 * 领养导航注册项。对齐 router/modules/adoption.js 的路由 name。
 */
export default {
  member: [
    { to: 'adoptions', text: '领养', dataTestId: 'nav-adoptions' },
    { to: 'my-adoptions', text: '我的领养', dataTestId: 'nav-my-adoptions' }
  ],
  admin: [
    { to: 'admin-adoptions', text: '领养审核', dataTestId: 'nav-admin-adoptions' }
  ]
}
