/**
 * 导航注册项底座。
 *
 * 每个导航项是一个普通对象：
 *   { to: 'router-name 或 path', text: '导航文案', requireAuth?: boolean,
 *     dataTestId?: string, group?: 'public'|'member'|'admin' }
 *
 * 规则：
 *   - text: 导航可见文案；
 *   - to: 优先用路由 name（经受 vue-router 解析），也允许 path（如 '/'）；
 *   - requireAuth: true 表示登录态可见，缺省为 false；
 *   - dataTestId: 测试用 data-test-id，便于 spec 断言（可选）；
 *   - group: 'public'（始终）/ 'member'（登录后普通入口）/ 'admin'（后台管理）。
 *
 * 各业务模块只新增自己的 navigation/modules/xxx.js 并在 navigation/index.js
 * 的列表里追加 import + spread，App.vue 不再硬编码每条 router-link。
 * 这样新增业务入口时不再共同编辑 App.vue，避免多线并发冲突。
 */
export default {
  public: [
    { to: '/', text: '首页', dataTestId: 'nav-home' }
  ],
  member: [
    { to: 'goods', text: '商品', dataTestId: 'nav-goods' },
    { to: 'my-orders', text: '我的订单', dataTestId: 'nav-my-orders' },
    { to: 'pets', text: '宠物', dataTestId: 'nav-pets' },
    { to: 'my-pets', text: '我的宠物', dataTestId: 'nav-my-pets' },
    { to: 'ai-chat', text: 'AI 对话', dataTestId: 'nav-ai-chat' },
    { to: 'profile', text: '个人资料', dataTestId: 'nav-profile' },
    { to: 'notifications', text: '通知中心', dataTestId: 'nav-notifications' }
  ],
  admin: [
    { to: 'admin-users', text: '用户管理', dataTestId: 'nav-admin-users' },
    { to: 'admin-system', text: '系统管理', dataTestId: 'nav-admin-system' },
    { to: 'admin-goods', text: '商品管理', dataTestId: 'nav-admin-goods' },
    { to: 'admin-pets', text: '宠物管理', dataTestId: 'nav-admin-pets' },
    { to: 'admin-orders', text: '订单管理', dataTestId: 'nav-admin-orders' }
  ]
}
