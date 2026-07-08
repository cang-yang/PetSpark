import Vue from 'vue'
import VueRouter from 'vue-router'
import HomeView from '../views/HomeView.vue'
import LoginView from '../views/LoginView.vue'
import RegisterView from '../views/RegisterView.vue'
import ForgotPasswordView from '../views/ForgotPasswordView.vue'
import NotificationsView from '../views/NotificationsView.vue'

Vue.use(VueRouter)

export default new VueRouter({
  mode: 'history',
  routes: [
    { path: '/', name: 'home', component: HomeView },
    { path: '/login', name: 'login', component: LoginView },
    { path: '/register', name: 'register', component: RegisterView },
    { path: '/forgot-password', name: 'forgot-password', component: ForgotPasswordView },
    { path: '/notifications', name: 'notifications', component: NotificationsView },
    { path: '/goods', name: 'goods', component: () => import('../views/GoodsListView.vue') },
    { path: '/goods/:id', name: 'goods-detail', component: () => import('../views/GoodsDetailView.vue') },
    { path: '/pets', name: 'pets', component: () => import('../views/PetsView.vue') },
    { path: '/my/pets', name: 'my-pets', component: () => import('../views/MyPetsView.vue') },
    { path: '/profile', name: 'profile', component: () => import('../views/ProfileView.vue') },
    { path: '/admin/users', name: 'admin-users', component: () => import('../views/AdminUsersView.vue') },
    { path: '/admin/system', name: 'admin-system', component: () => import('../views/SystemAdminView.vue') },
    { path: '/admin/goods', name: 'admin-goods', component: () => import('../views/AdminGoodsView.vue') },
    { path: '/admin/pets', name: 'admin-pets', component: () => import('../views/AdminPetsView.vue') }
  ]
})
