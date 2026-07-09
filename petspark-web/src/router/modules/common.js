/**
 * 公共路由模块：登录、注册、密码找回、首页、通知中心、个人资料。
 * 无需登录态的通用入口（除首页外的鉴权页）也归在此处。
 *
 * 后续业务模块请只新增自己的 router/modules/xxx.js，不要回到本文件或
 * router/index.js 里集中追加路由——index 仅负责收集各模块。
 */
import HomeView from '../../views/HomeView.vue'
import LoginView from '../../views/LoginView.vue'
import RegisterView from '../../views/RegisterView.vue'
import ForgotPasswordView from '../../views/ForgotPasswordView.vue'
import NotificationsView from '../../views/NotificationsView.vue'

export default [
  { path: '/', name: 'home', component: HomeView },
  { path: '/login', name: 'login', component: LoginView },
  { path: '/register', name: 'register', component: RegisterView },
  { path: '/forgot-password', name: 'forgot-password', component: ForgotPasswordView },
  { path: '/notifications', name: 'notifications', component: NotificationsView },
  { path: '/profile', name: 'profile', component: () => import('../../views/ProfileView.vue') }
]
