import http from '@/api/http'
import {
  cancelServiceBooking,
  createServiceBooking,
  createServiceItem,
  createServiceResource,
  createServiceSlots,
  deleteServiceItem,
  exceptionServiceBooking,
  getServiceBooking,
  getServiceItem,
  listAdminServiceBookings,
  listMyServiceBookings,
  listServiceItems,
  listServiceResources,
  listServiceSlots,
  transitionServiceBooking,
  updateServiceItem,
  updateServiceResource
} from '@/api/service'

jest.mock('@/api/http', () => ({
  get: jest.fn(),
  post: jest.fn(),
  put: jest.fn(),
  delete: jest.fn()
}))

describe('service API', () => {
  beforeEach(() => {
    Object.values(http).forEach(fn => fn.mockReset())
  })

  it('calls public service browse endpoints', () => {
    listServiceItems({ keyword: '美容', page: 1, size: 10 })
    getServiceItem('svc-1')
    listServiceResources({ serviceItemId: 'svc-1', page: 1, size: 10 })
    listServiceSlots({ serviceItemId: 'svc-1', slotDate: '2026-07-10', page: 1, size: 10 })

    expect(http.get).toHaveBeenCalledWith('/api/v1/services/items', { params: { keyword: '美容', page: 1, size: 10 } })
    expect(http.get).toHaveBeenCalledWith('/api/v1/services/items/svc-1')
    expect(http.get).toHaveBeenCalledWith('/api/v1/services/resources', { params: { serviceItemId: 'svc-1', page: 1, size: 10 } })
    expect(http.get).toHaveBeenCalledWith('/api/v1/services/slots', { params: { serviceItemId: 'svc-1', slotDate: '2026-07-10', page: 1, size: 10 } })
  })

  it('creates and manages my bookings', () => {
    const payload = {
      serviceItemId: 'svc-1', resourceId: 'res-1', slotId: 'slot-1',
      petId: 'pet-1', customerName: '张三', customerPhone: '13800000000', remark: '备注'
    }
    createServiceBooking(payload)
    expect(http.post).toHaveBeenCalledWith('/api/v1/services/bookings', payload)

    listMyServiceBookings({ status: 'CONFIRMED', page: 1, size: 10 })
    expect(http.get).toHaveBeenCalledWith('/api/v1/services/bookings', { params: { status: 'CONFIRMED', page: 1, size: 10 } })

    getServiceBooking('b-1')
    expect(http.get).toHaveBeenCalledWith('/api/v1/services/bookings/b-1')

    cancelServiceBooking('b-1', { reason: '行程冲突', version: 2 })
    expect(http.post).toHaveBeenCalledWith('/api/v1/services/bookings/b-1/cancel', { reason: '行程冲突', version: 2 })

    exceptionServiceBooking('b-1', { reason: '宠物不适', version: 2 })
    expect(http.post).toHaveBeenCalledWith('/api/v1/services/bookings/b-1/exception', { reason: '宠物不适', version: 2 })
  })

  it('calls admin fulfillment and list endpoints', () => {
    transitionServiceBooking('b-1', { status: 'IN_PROGRESS', note: '开始服务', version: 3 })
    expect(http.post).toHaveBeenCalledWith('/api/v1/admin/services/bookings/b-1/transition', { status: 'IN_PROGRESS', note: '开始服务', version: 3 })

    listAdminServiceBookings({ status: 'CONFIRMED', keyword: 'SVC', page: 1, size: 10 })
    expect(http.get).toHaveBeenCalledWith('/api/v1/admin/services/bookings', { params: { status: 'CONFIRMED', keyword: 'SVC', page: 1, size: 10 } })
  })

  it('calls admin item/resource/slot management endpoints', () => {
    const itemPayload = { kind: 'GENERIC', code: 'GROOM-1', name: '基础美容', basePrice: 120, status: 'ACTIVE' }
    createServiceItem(itemPayload)
    expect(http.post).toHaveBeenCalledWith('/api/v1/admin/services/items', itemPayload)

    updateServiceItem('svc-1', itemPayload)
    expect(http.put).toHaveBeenCalledWith('/api/v1/admin/services/items/svc-1', itemPayload)

    deleteServiceItem('svc-1')
    expect(http.delete).toHaveBeenCalledWith('/api/v1/admin/services/items/svc-1')

    const resourcePayload = { serviceItemId: 'svc-1', name: '美容师小王', capacity: 2, status: 'ACTIVE' }
    createServiceResource(resourcePayload)
    expect(http.post).toHaveBeenCalledWith('/api/v1/admin/services/resources', resourcePayload)

    updateServiceResource('res-1', resourcePayload)
    expect(http.put).toHaveBeenCalledWith('/api/v1/admin/services/resources/res-1', resourcePayload)

    const slotsPayload = { resourceId: 'res-1', slots: [{ startAt: '2026-07-10T09:00:00Z', endAt: '2026-07-10T10:00:00Z', capacity: 2 }] }
    createServiceSlots(slotsPayload)
    expect(http.post).toHaveBeenCalledWith('/api/v1/admin/services/slots', slotsPayload)
  })
})
