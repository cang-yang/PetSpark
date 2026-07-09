jest.mock('@/api/auth', () => ({
  issueCaptcha: jest.fn(),
  login: jest.fn(),
  register: jest.fn()
}))
jest.mock('@/api/notifications', () => ({
  getUnreadNotificationCount: jest.fn(),
  listNotifications: jest.fn(),
  markNotificationRead: jest.fn(),
  markAllNotificationsRead: jest.fn()
}))

import router from '@/router'

describe('auth routes', () => {
  it('exposes the password reset page', () => {
    const route = router.options.routes.find((candidate) => candidate.path === '/forgot-password')
    expect(route).toBeDefined()
    expect(route.name).toBe('forgot-password')
  })
})
