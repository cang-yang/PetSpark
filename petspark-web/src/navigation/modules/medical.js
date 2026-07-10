/**
 * 医疗服务导航模块。
 * member：医疗服务浏览、我的医疗预约；admin：医疗管理。
 */
export default {
  member: [
    { to: 'medical', text: '宠物医疗', icon: 'medical', dataTestId: 'nav-medical' },
    { to: 'my-medical-bookings', text: '我的医疗预约', icon: 'medical', dataTestId: 'nav-my-medical-bookings' }
  ],
  admin: [
    { to: 'admin-medical', text: '医疗管理', icon: 'medical', dataTestId: 'nav-admin-medical' }
  ]
}
