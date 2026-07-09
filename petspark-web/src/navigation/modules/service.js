/**
 * 服务预约导航模块。
 * member：服务浏览、我的服务预约；
 * admin：服务管理（项目/资源/窗口/履约）。
 * 在 navigation/index.js 的 modules 数组里追加即可纳入聚合，不改 App.vue。
 */
export default {
  member: [
    { to: 'services', text: '服务', dataTestId: 'nav-services' },
    { to: 'my-service-bookings', text: '我的服务预约', dataTestId: 'nav-my-service-bookings' }
  ],
  admin: [
    { to: 'admin-services', text: '服务管理', dataTestId: 'nav-admin-services' }
  ]
}
