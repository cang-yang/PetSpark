/**
 * 美容服务导航模块。入口独立于通用服务导航，页面内仍复用 service API。
 */
export default {
  member: [
    { to: 'beauty', text: '宠物美容', icon: 'beauty', dataTestId: 'nav-beauty' },
    { to: 'my-beauty-bookings', text: '我的美容预约', icon: 'beauty', dataTestId: 'nav-my-beauty-bookings' }
  ],
  admin: [
    { to: 'admin-beauty', text: '美容管理', icon: 'beauty', dataTestId: 'nav-admin-beauty' }
  ]
}
