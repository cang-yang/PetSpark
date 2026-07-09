/** 流浪救助导航注册项。对齐 router/modules/stray.js 的路由 name。 */
export default {
  member: [
    { to: 'stray-clues', text: '流浪救助', dataTestId: 'nav-stray-clues' },
    { to: 'my-stray-clues', text: '我的线索', dataTestId: 'nav-my-stray-clues' }
  ],
  admin: [
    { to: 'admin-stray-clues', text: '救助线索', dataTestId: 'nav-admin-stray-clues' }
  ]
}
