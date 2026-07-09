import http from '@/api/http'
import {
  createBanner,
  deleteBanner,
  getAdminBanner,
  listActiveBanners,
  listAdminBanners,
  updateBanner,
  updateBannerOrder,
  updateBannerStatus
} from '@/api/banner'

jest.mock('@/api/http', () => ({
  get: jest.fn(),
  post: jest.fn(),
  put: jest.fn(),
  patch: jest.fn(),
  delete: jest.fn()
}))

describe('banner API', () => {
  beforeEach(() => {
    Object.values(http).forEach(fn => fn.mockReset())
  })

  it('calls public banner endpoint', () => {
    listActiveBanners({ limit: 5 })

    expect(http.get).toHaveBeenCalledWith('/api/v1/banners', { params: { limit: 5 } })
  })

  it('calls backend banner management endpoints', () => {
    const payload = { title: '首页活动', version: 2 }
    listAdminBanners({ status: 'ACTIVE' })
    getAdminBanner('b-1')
    createBanner(payload)
    updateBanner('b-1', payload)
    updateBannerStatus('b-1', { status: 'INACTIVE', version: 2 })
    updateBannerOrder('b-1', { sortOrder: 3, version: 3 })
    deleteBanner('b-1', 4)

    expect(http.get).toHaveBeenCalledWith('/api/v1/admin/banners', { params: { status: 'ACTIVE' } })
    expect(http.get).toHaveBeenCalledWith('/api/v1/admin/banners/b-1')
    expect(http.post).toHaveBeenCalledWith('/api/v1/admin/banners', payload)
    expect(http.put).toHaveBeenCalledWith('/api/v1/admin/banners/b-1', payload)
    expect(http.patch).toHaveBeenCalledWith('/api/v1/admin/banners/b-1/status', { status: 'INACTIVE', version: 2 })
    expect(http.patch).toHaveBeenCalledWith('/api/v1/admin/banners/b-1/order', { sortOrder: 3, version: 3 })
    expect(http.delete).toHaveBeenCalledWith('/api/v1/admin/banners/b-1', { params: { version: 4 } })
  })
})
