jest.mock('@/api/service', () => ({
  listAdminServiceBookings: jest.fn(),
  transitionServiceBooking: jest.fn()
}))

import { shallowMount } from '@vue/test-utils'
import AdminServiceView from '@/views/AdminServiceView.vue'
import { listAdminServiceBookings, transitionServiceBooking } from '@/api/service'

describe('AdminServiceView', () => {
  beforeEach(() => {
    jest.clearAllMocks()
    listAdminServiceBookings.mockResolvedValue({
      data: {
        items: [
          { id: 'b-1', bookingNo: 'SVC-1', userId: 'u-1', status: 'CONFIRMED', unitPrice: 120, startAt: '2026-07-10T09:00:00Z', version: 1 }
        ],
        page: 1, size: 10, total: 1
      }
    })
    transitionServiceBooking.mockResolvedValue({ data: { id: 'b-1', status: 'IN_PROGRESS', version: 2 } })
  })

  it('loads admin service bookings on mount', async () => {
    const wrapper = shallowMount(AdminServiceView, {
      mocks: { $message: { success: jest.fn(), error: jest.fn() } },
      stubs: ['el-table', 'el-table-column', 'el-tag', 'el-button', 'el-input', 'el-select', 'el-option']
    })
    await flush()

    expect(listAdminServiceBookings).toHaveBeenCalledWith({ keyword: undefined, status: undefined, page: 1, size: 10 })
    expect(wrapper.vm.bookings).toHaveLength(1)
  })

  it('transitions booking state with version', async () => {
    const wrapper = shallowMount(AdminServiceView, {
      mocks: { $message: { success: jest.fn(), error: jest.fn() } },
      stubs: ['el-table', 'el-table-column', 'el-tag', 'el-button', 'el-input', 'el-select', 'el-option']
    })
    await flush()

    await wrapper.vm.transition(wrapper.vm.bookings[0], 'IN_PROGRESS', '开始服务')
    expect(transitionServiceBooking).toHaveBeenCalledWith('b-1', { status: 'IN_PROGRESS', note: '开始服务', version: 1 })
  })
})

function flush() {
  return new Promise(resolve => setTimeout(resolve, 0))
}
