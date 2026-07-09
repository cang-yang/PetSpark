// 显式 mock 视图组件引用的 API 层，使路线表态模块在 jest 下可不加载 ESM axios。
jest.mock('@/api/auth', () => ({
  issueCaptcha: jest.fn(),
  login: jest.fn(),
  register: jest.fn(),
  logout: jest.fn()
}))
jest.mock('@/api/notifications', () => ({
  listNotifications: jest.fn(),
  markNotificationRead: jest.fn(),
  markAllNotificationsRead: jest.fn()
}))
jest.mock('@/api/http', () => ({ default: { get: jest.fn(), post: jest.fn(), put: jest.fn(), delete: jest.fn() } }))

import commonRoutes from '@/router/modules/common'
import catalogRoutes from '@/router/modules/catalog'
import petRoutes from '@/router/modules/pet'
import adoptionRoutes from '@/router/modules/adoption'
import aiRoutes from '@/router/modules/ai'
import systemRoutes from '@/router/modules/system'
import router from '@/router'

import navigation from '@/navigation'

/**
 * PR-FE-NAV-01 守护测试：确保路由/导航注册底座不会漏掉迁移前的既有入口。
 * 基线来自重构前 router/index.js 的全部路由 name/path，
 * 以及 App.vue 重构前的全部 nav 入口。后续业务 PR 不应破坏这里列出的基线集合。
 */

describe('frontend route registry baseline', () => {
  const baselineNames = [
    'home', 'login', 'register', 'forgot-password', 'notifications',
    'goods', 'goods-detail', 'my-orders',
    'pets', 'my-pets', 'pet-health',
    'ai-chat',
    'profile',
    'admin-users', 'admin-system', 'admin-goods', 'admin-pets', 'admin-orders'
  ]
  const expectedPaths = [
    '/', '/login', '/register', '/forgot-password', '/notifications',
    '/goods', '/goods/:id', '/my/orders',
    '/pets', '/my/pets', '/my/pets/:id/health',
    '/ai/chat',
    '/profile',
    '/admin/users', '/admin/system', '/admin/goods', '/admin/pets', '/admin/orders'
  ]

  it('preserves every baseline route name after modularization', () => {
    const names = router.options.routes.map((r) => r.name).filter(Boolean)
    const missing = baselineNames.filter((n) => !names.includes(n))
    expect(missing).toEqual([])
  })

  it('preserves every baseline route path after modularization', () => {
    const paths = router.options.routes.map((r) => r.path)
    const missing = expectedPaths.filter((p) => !paths.includes(p))
    expect(missing).toEqual([])
  })

  it('keeps the modular split across modules with expected counts', () => {
    expect(commonRoutes.length).toBeGreaterThanOrEqual(6)
    expect(catalogRoutes.length).toBe(5)
    expect(petRoutes.length).toBe(4)
    expect(adoptionRoutes.length).toBe(3)
    expect(aiRoutes.length).toBe(1)
    expect(systemRoutes.length).toBe(2)
    expect(commonRoutes.length + catalogRoutes.length + petRoutes.length
      + adoptionRoutes.length + aiRoutes.length + systemRoutes.length).toBe(router.options.routes.length)
  })
})

describe('navigation registry baseline', () => {
  it('exposes the public "首页" entry', () => {
    const home = navigation.publicNav.find((e) => e.to === '/')
    expect(home).toBeDefined()
    expect(home.text).toBe('首页')
  })

  it('keeps all pre-refactor member nav entries by name', () => {
    const memberNames = navigation.memberNav.map((e) => e.to)
    const expected = [
      'goods', 'my-orders', 'pets', 'my-pets', 'ai-chat', 'profile', 'notifications'
    ]
    expect(expected.filter((n) => !memberNames.includes(n))).toEqual([])
  })

  it('keeps all pre-refactor admin nav entries by name', () => {
    const adminNames = navigation.adminNav.map((e) => e.to)
    const expected = [
      'admin-users', 'admin-system', 'admin-goods', 'admin-pets', 'admin-orders'
    ]
    expect(expected.filter((n) => !adminNames.includes(n))).toEqual([])
  })

  it('every member/admin entry has a stable dataTestId for specs', () => {
    for (const entry of [...navigation.memberNav, ...navigation.adminNav]) {
      expect(entry.dataTestId).toMatch(/^nav-/)
    }
  })
})
