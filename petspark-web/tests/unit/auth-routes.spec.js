jest.mock('@/api/auth', () => ({
  issueCaptcha: jest.fn(),
  login: jest.fn(),
  register: jest.fn()
}))

import router from '@/router'

describe('auth routes', () => {
  it('exposes the password reset page', () => {
    const route = router.options.routes.find((candidate) => candidate.path === '/forgot-password')
    expect(route).toBeDefined()
    expect(route.name).toBe('forgot-password')
  })
})
