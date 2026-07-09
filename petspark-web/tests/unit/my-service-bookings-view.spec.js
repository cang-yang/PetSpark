jest.mock('@/api/service', () => ({
  listMyServiceBookings: jest.fn(),
  getServiceBooking: jest.fn(),
  cancelServiceBooking: jest.fn(),
  exceptionServiceBooking: jest.fn()
}))

import { shallowMount } from '@vue/test-utils'
import MyServiceBookingsView from '@/views/MyServiceBookingsView.vue'
import {
  listMyServiceBookings,
  getServiceBooking,
  cancelServiceBooking,
  exceptionServiceBooking
} from '@/api/service'

describe('MyServiceBookingsView', () => {
  beforeEach(() => {
    jest.clearAllMocks()
    listMyServiceBookings.mockResolvedValue({
      data: {
        items: [
          { id: 'b-1', bookingNo: 'SVC-1', status: 'CONFIRMED', unitPrice: 120, startAt: '2026-07-10T09:00:00Z', version: 1 }
        ],
        page: 1, size: 10, total: 1
      }
    })
    getServiceBooking.mockResolvedValue({
      data: { id: 'b-1', bookingNo: 'SVC-1', status: 'CONFIRMED', version: 1, customerName: '张三', customerPhone: '13800000000', startAt: '2026-07-10T09:00:00Z', endAt: '2026-07-10T10:00:00Z' }
    })
    cancelServiceBooking.mockResolvedValue({ data: { id: 'b-1', status: 'CANCELLED', version: 2 } })
    exceptionServiceBooking.mockResolvedValue({ data: { id: 'b-1', status: 'EXCEPTION', version: 2 } })
  })

  it('loads my service bookings on mount', async () => {
    const wrapper = shallowMount(MyServiceBookingsView, {
      mocks: { $message: { success: jest.fn(), error: jest.fn(), warning: jest.fn() } },
      stubs: ['el-table', 'el-table-column', 'el-tag', 'el-button', 'el-input', 'el-select', 'el-option', 'el-dialog']
    })
    await flush()

    expect(listMyServiceBookings).toHaveBeenCalledWith({ status: undefined, page: 1, size: 10 })
    expect(wrapper.vm.bookings).toHaveLength(1)
  })

  it('opens detail and cancels with version', async () => {
    const wrapper = shallowMount(MyServiceBookingsView, {
      mocks: { $message: { success: jest.fn(), error: jest.fn(), warning: jest.fn() } },
      stubs: ['el-table', 'el-table-column', 'el-tag', 'el-button', 'el-input', 'el-select', 'el-option', 'el-dialog']
    })
    await flush()

    await wrapper.vm.openDetail(wrapper.vm.bookings[0])
    expect(getServiceBooking).toHaveBeenCalledWith('b-1')
    expect(wrapper.vm.current.id).toBe('b-1')

    wrapper.setData({ cancelReason: '行程冲突' })
    await wrapper.vm.submitCancel()
    expect(cancelServiceBooking).toHaveBeenCalledWith('b-1', { reason: '行程冲突', version: 1 })
  })

  it('opens detail and exceptions with version', async () => {
    const wrapper = shallowMount(MyServiceBookingsView, {
      mocks: { $message: { success: jest.fn(), error: jest.fn(), warning: jest.fn() } },
      stubs: ['el-table', 'el-table-column', 'el-tag', 'el-button', 'el-input', 'el-select', 'el-option', 'el-dialog']
    })
    await flush()

    await wrapper.vm.openDetail(wrapper.vm.bookings[0])
    wrapper.setData({ exceptionReason: '宠物不适' })
    await wrapper.vm.submitException()
    expect(exceptionServiceBooking).toHaveBeenCalledWith('b-1', { reason: '宠物不适', version: 1 })
  })
})

function flush() {
  return new Promise(resolve => setTimeout(resolve, 0))
}
