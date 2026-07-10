import Vue from 'vue'
import VueRouter from 'vue-router'
import commonRoutes from './modules/common'
import catalogRoutes from './modules/catalog'
import petRoutes from './modules/pet'
import adoptionRoutes from './modules/adoption'
import aiRoutes from './modules/ai'
import systemRoutes from './modules/system'
import boardingRoutes from './modules/boarding'
import serviceRoutes from './modules/service'
import trainingRoutes from './modules/training'
import beautyRoutes from './modules/beauty'
import medicalRoutes from './modules/medical'
import communityRoutes from './modules/community'
import strayRoutes from './modules/stray'
import bannerRoutes from './modules/banner'
import dashboardRoutes from './modules/dashboard'
import { withLayoutMeta } from './layout'

Vue.use(VueRouter)

/**
 * 路由注册底座：index 只收集各 router/modules/* 模块并组合路由。
 * 后续业务 PR 只新增自己的 router/modules/xxx.js 并在此追加 import + concat，
 * 不再集中在本文件硬编码每条路由。模块顺序即路由匹配顺序；公共模块在前，
 * 业务模块在后，保证精确路径不被通配/动态路径误匹配。
 */
const routes = withLayoutMeta([
  ...commonRoutes,
  ...catalogRoutes,
  ...petRoutes,
  ...adoptionRoutes,
  ...aiRoutes,
  ...systemRoutes,
  ...boardingRoutes,
  ...serviceRoutes,
  ...trainingRoutes,
  ...beautyRoutes,
  ...medicalRoutes,
  ...communityRoutes,
  ...strayRoutes,
  ...bannerRoutes,
  ...dashboardRoutes
])

export default new VueRouter({
  mode: 'history',
  routes
})
