import http from '@/api/http'
import {
  adjustGoodsStock,
  createGoods,
  getGoods,
  listAdminGoods,
  listGoods,
  updateGoods,
  updateGoodsStatus
} from '@/api/catalog'

jest.mock('@/api/http', () => ({
  get: jest.fn(),
  post: jest.fn(),
  put: jest.fn(),
  patch: jest.fn()
}))

describe('catalog API', () => {
  beforeEach(() => {
    Object.values(http).forEach(fn => fn.mockReset())
  })

  it('calls public goods endpoints', () => {
    listGoods({ keyword: '猫粮', page: 1 })
    getGoods('g-1')

    expect(http.get).toHaveBeenCalledWith('/api/v1/goods', { params: { keyword: '猫粮', page: 1 } })
    expect(http.get).toHaveBeenCalledWith('/api/v1/goods/g-1')
  })

  it('calls backend goods management endpoints', () => {
    const payload = { sku: 'CAT-1', version: 2 }
    listAdminGoods({ status: 'ACTIVE' })
    createGoods(payload)
    updateGoods('g-1', payload)
    updateGoodsStatus('g-1', { status: 'INACTIVE', version: 2 })
    adjustGoodsStock('g-1', { delta: -1, reason: '盘点', version: 3 })

    expect(http.get).toHaveBeenCalledWith('/api/v1/admin/goods', { params: { status: 'ACTIVE' } })
    expect(http.post).toHaveBeenCalledWith('/api/v1/admin/goods', payload)
    expect(http.put).toHaveBeenCalledWith('/api/v1/admin/goods/g-1', payload)
    expect(http.patch).toHaveBeenCalledWith('/api/v1/admin/goods/g-1/status', { status: 'INACTIVE', version: 2 })
    expect(http.post).toHaveBeenCalledWith('/api/v1/admin/goods/g-1/stock-adjustments', { delta: -1, reason: '盘点', version: 3 })
  })
})
