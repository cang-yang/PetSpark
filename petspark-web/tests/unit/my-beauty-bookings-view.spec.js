jest.mock('@/api/service', () => ({
  listMyServiceBookings: jest.fn(),
  getServiceBooking: jest.fn(),
  cancelServiceBooking: jest.fn()
}))

import { shallowMount } from '@vue/test-utils'
import MyBeautyBookingsView from '@/views/MyBeautyBookingsView.vue'
import { listMyServiceBookings, getServiceBooking, cancelServiceBooking } from '@/api/service'

describe('MyBeautyBookingsView', () => {
  beforeEach(() => {
    jest.clearAllMocks()
    listMyServiceBookings.mockResolvedValue({
      data: {
        items: [
          { id: 'b-1', bookingNo: 'SVC-1', kind: 'BEAUTY', serviceItemName: '基础美容', status: 'CONFIRMED', startAt: '2026-07-10T09:00:00Z', version: 1 }
        ],
        page: 1, size: 10, total: 1
      }
    })
    getServiceBooking.mockResolvedValue({
      data: { id: 'b-1', bookingNo: 'SVC-1', kind: 'BEAUTY', serviceItemName: '基础美容', status: 'CONFIRMED', version: 1, customerName: '张三', customerPhone: '13800000000' }
    })
    cancelServiceBooking.mockResolvedValue({ data: { id: 'b-1', status: 'CANCELLED', version: 2 } })
  })

  it('loads only BEAUTY bookings on mount', async () => {
    const wrapper = shallowMount(MyBeautyBookingsView, {
      mocks: { $message: { success: jest.fn(), error: jest.fn() } },
      stubs: ['el-table', 'el-table-column', 'el-tag', 'el-button', 'el-input', 'el-select', 'el-option', 'el-dialog']
    })
    await flush()

    expect(listMyServiceBookings).toHaveBeenCalledWith({ kind: 'BEAUTY', status: undefined, page: 1, size: 10 })
    expect(wrapper.vm.bookings).toHaveLength(1)
  })

  it('opens detail and cancels with optimistic version', async () => {
    const wrapper = shallowMount(MyBeautyBookingsView, {
      mocks: { $message: { success: jest.fn(), error: jest.fn() } },
      stubs: ['el-table', 'el-table-column', 'el-tag', 'el-button', 'el-input', 'el-select', 'el-option', 'el-dialog']
    })
    await flush()

    await wrapper.vm.openDetail(wrapper.vm.bookings[0])
    wrapper.setData({ cancelReason: '行程冲突' })
    await wrapper.vm.submitCancel()

    expect(getServiceBooking).toHaveBeenCalledWith('b-1')
    expect(cancelServiceBooking).toHaveBeenCalledWith('b-1', { reason: '行程冲突', version: 1 })
  })
})

function flush() {
  return new Promise(resolve => setTimeout(resolve, 0))
}
