import http from '@/api/http'
import {
  cancelOrder,
  createOrder,
  getOrder,
  listAdminOrders,
  listMyOrders,
  previewOrder,
  transitionOrder
} from '@/api/orders'

jest.mock('@/api/http', () => ({
  get: jest.fn(),
  post: jest.fn(),
  put: jest.fn(),
  patch: jest.fn()
}))

describe('orders API', () => {
  beforeEach(() => {
    Object.values(http).forEach(fn => fn.mockReset())
  })

  it('calls preview endpoint', () => {
    const payload = { lines: [{ goodsId: 'g-1', quantity: 2 }] }
    previewOrder(payload)
    expect(http.post).toHaveBeenCalledWith('/api/v1/orders/preview', payload)
  })

  it('attaches Idempotency-Key header on create', () => {
    const payload = { lines: [{ goodsId: 'g-1', quantity: 1 }] }
    createOrder(payload, 'idem-123')
    expect(http.post).toHaveBeenCalledWith('/api/v1/orders', payload, { headers: { 'Idempotency-Key': 'idem-123' } })
  })

  it('omits header when no idempotency key', () => {
    const payload = { lines: [{ goodsId: 'g-1', quantity: 1 }] }
    createOrder(payload)
    expect(http.post).toHaveBeenCalledWith('/api/v1/orders', payload, { headers: {} })
  })

  it('calls user order endpoints', () => {
    listMyOrders({ status: 'CREATED', page: 1, size: 10 })
    getOrder('o-1')
    cancelOrder('o-1', { reason: '不想要了', version: 3 })

    expect(http.get).toHaveBeenCalledWith('/api/v1/orders', { params: { status: 'CREATED', page: 1, size: 10 } })
    expect(http.get).toHaveBeenCalledWith('/api/v1/orders/o-1')
    expect(http.post).toHaveBeenCalledWith('/api/v1/orders/o-1/cancel', { reason: '不想要了', version: 3 })
  })

  it('calls admin order endpoints', () => {
    listAdminOrders({ status: 'PROCESSING', keyword: 'ORD', page: 1, size: 10 })
    transitionOrder('o-1', { status: 'COMPLETED', note: '完成', version: 4 })

    expect(http.get).toHaveBeenCalledWith('/api/v1/admin/orders', { params: { status: 'PROCESSING', keyword: 'ORD', page: 1, size: 10 } })
    expect(http.post).toHaveBeenCalledWith('/api/v1/admin/orders/o-1/transition', { status: 'COMPLETED', note: '完成', version: 4 })
  })
})
