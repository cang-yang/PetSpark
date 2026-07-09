/**
 * 寄养导航模块。会员侧发起寄养、查看我的寄养；后台侧房间管理与寄养预约履约。
 * 在 navigation/index.js 的 modules 列表里追加 import + spread 即可合入导航，
 * App.vue 不再硬编码每条 router-link。
 */
export default {
  member: [
    { to: 'boarding-new', text: '发起寄养', dataTestId: 'nav-boarding-new' },
    { to: 'my-boarding', text: '我的寄养', dataTestId: 'nav-my-boarding' }
  ],
  admin: [
    { to: 'admin-rooms', text: '房间管理', dataTestId: 'nav-admin-rooms' },
    { to: 'admin-boarding', text: '寄养管理', dataTestId: 'nav-admin-boarding' }
  ]
}
