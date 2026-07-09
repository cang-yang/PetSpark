jest.mock('@/api/service', () => ({
  createServiceItem: jest.fn(),
  listAdminServiceBookings: jest.fn(),
  transitionServiceBooking: jest.fn()
}))

import { shallowMount } from '@vue/test-utils'
import AdminBeautyView from '@/views/AdminBeautyView.vue'
import { createServiceItem, listAdminServiceBookings, transitionServiceBooking } from '@/api/service'

describe('AdminBeautyView', () => {
  beforeEach(() => {
    jest.clearAllMocks()
    listAdminServiceBookings.mockResolvedValue({
      data: {
        items: [
          { id: 'b-1', bookingNo: 'SVC-1', kind: 'BEAUTY', serviceItemName: '基础美容', status: 'CONFIRMED', startAt: '2026-07-10T09:00:00Z', version: 1 }
        ],
        page: 1, size: 10, total: 1
      }
    })
    createServiceItem.mockResolvedValue({ data: { id: 'item-1', kind: 'BEAUTY' } })
    transitionServiceBooking.mockResolvedValue({ data: { id: 'b-1', status: 'IN_PROGRESS', version: 2 } })
  })

  it('loads admin BEAUTY bookings on mount', async () => {
    const wrapper = shallowMount(AdminBeautyView, {
      mocks: { $message: { success: jest.fn(), error: jest.fn(), warning: jest.fn() } },
      stubs: ['el-card', 'el-form', 'el-form-item', 'el-table', 'el-table-column', 'el-tag', 'el-button', 'el-input', 'el-select', 'el-option']
    })
    await flush()

    expect(listAdminServiceBookings).toHaveBeenCalledWith({ kind: 'BEAUTY', keyword: undefined, status: undefined, page: 1, size: 10 })
    expect(wrapper.vm.bookings).toHaveLength(1)
  })

  it('creates a BEAUTY service item and transitions booking state', async () => {
    const wrapper = shallowMount(AdminBeautyView, {
      mocks: { $message: { success: jest.fn(), error: jest.fn(), warning: jest.fn() } },
      stubs: ['el-card', 'el-form', 'el-form-item', 'el-table', 'el-table-column', 'el-tag', 'el-button', 'el-input', 'el-select', 'el-option']
    })
    await flush()

    wrapper.setData({ itemForm: { code: 'BEAUTY-IT', name: '测试美容', basePrice: 128, qualification: '', availabilityNote: '', exceptionRule: '' } })
    await wrapper.vm.createItem()
    expect(createServiceItem).toHaveBeenCalledWith(expect.objectContaining({ kind: 'BEAUTY', code: 'BEAUTY-IT', status: 'ACTIVE' }))

    await wrapper.vm.transition(wrapper.vm.bookings[0], 'IN_PROGRESS', '开始美容服务')
    expect(transitionServiceBooking).toHaveBeenCalledWith('b-1', { status: 'IN_PROGRESS', note: '开始美容服务', version: 1 })
  })
})

function flush() {
  return new Promise(resolve => setTimeout(resolve, 0))
}
