import http from '@/api/http'
import {
  refreshSession,
  logout,
  requestPasswordResetCode,
  resetPassword
} from '@/api/auth'

jest.mock('@/api/http', () => ({
  post: jest.fn()
}))

describe('auth session API', () => {
  beforeEach(() => {
    http.post.mockReset()
  })

  it('uses the refresh cookie for session rotation', () => {
    refreshSession()
    expect(http.post).toHaveBeenCalledWith('/api/v1/auth/refresh')
  })

  it('calls logout and password reset endpoints', () => {
    const codeRequest = { email: 'a@example.com', captchaId: 'c1', captchaAnswer: '12' }
    const resetRequest = { email: 'a@example.com', code: '123456', newPassword: 'N3wPass!' }

    logout()
    requestPasswordResetCode(codeRequest)
    resetPassword(resetRequest)

    expect(http.post).toHaveBeenNthCalledWith(1, '/api/v1/auth/logout')
    expect(http.post).toHaveBeenNthCalledWith(2, '/api/v1/auth/password-reset-codes', codeRequest)
    expect(http.post).toHaveBeenNthCalledWith(3, '/api/v1/auth/password-resets', resetRequest)
  })
})
