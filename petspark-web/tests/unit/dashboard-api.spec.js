import http from '@/api/http'
import { getDashboardSummary } from '@/api/dashboard'

jest.mock('@/api/http', () => ({
  get: jest.fn(),
  post: jest.fn(),
  put: jest.fn(),
  patch: jest.fn(),
  delete: jest.fn()
}))

describe('dashboard API', () => {
  beforeEach(() => {
    Object.values(http).forEach(fn => fn.mockReset())
  })

  it('calls admin dashboard summary endpoint', () => {
    getDashboardSummary()

    expect(http.get).toHaveBeenCalledWith('/api/v1/admin/dashboard')
  })
})
