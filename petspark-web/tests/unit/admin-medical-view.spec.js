jest.mock('@/api/service', () => ({
  createServiceItem: jest.fn(),
  listAdminServiceBookings: jest.fn(),
  transitionServiceBooking: jest.fn()
}))

import { shallowMount } from '@vue/test-utils'
import AdminMedicalView from '@/views/AdminMedicalView.vue'
import { createServiceItem, listAdminServiceBookings, transitionServiceBooking } from '@/api/service'

describe('AdminMedicalView', () => {
  beforeEach(() => {
    jest.clearAllMocks()
    listAdminServiceBookings.mockResolvedValue({
      data: {
        items: [
          { id: 'b-1', bookingNo: 'SVC-1', kind: 'MEDICAL', serviceItemName: '基础医疗', status: 'CONFIRMED', startAt: '2026-07-10T09:00:00Z', version: 1 }
        ],
        page: 1, size: 10, total: 1
      }
    })
    createServiceItem.mockResolvedValue({ data: { id: 'item-1', kind: 'MEDICAL' } })
    transitionServiceBooking.mockResolvedValue({ data: { id: 'b-1', status: 'IN_PROGRESS', version: 2 } })
  })

  it('loads admin MEDICAL bookings on mount', async () => {
    const wrapper = shallowMount(AdminMedicalView, {
      mocks: { $message: { success: jest.fn(), error: jest.fn(), warning: jest.fn() } },
      stubs: ['el-card', 'el-form', 'el-form-item', 'el-table', 'el-table-column', 'el-tag', 'el-button', 'el-input', 'el-select', 'el-option']
    })
    await flush()

    expect(listAdminServiceBookings).toHaveBeenCalledWith({ kind: 'MEDICAL', keyword: undefined, status: undefined, page: 1, size: 10 })
    expect(wrapper.vm.bookings).toHaveLength(1)
  })

  it('creates a MEDICAL service item and transitions booking state', async () => {
    const wrapper = shallowMount(AdminMedicalView, {
      mocks: { $message: { success: jest.fn(), error: jest.fn(), warning: jest.fn() } },
      stubs: ['el-card', 'el-form', 'el-form-item', 'el-table', 'el-table-column', 'el-tag', 'el-button', 'el-input', 'el-select', 'el-option']
    })
    await flush()

    wrapper.setData({ itemForm: { code: 'MEDICAL-IT', name: '测试医疗', basePrice: 128, qualification: '', availabilityNote: '', exceptionRule: '' } })
    await wrapper.vm.createItem()
    expect(createServiceItem).toHaveBeenCalledWith(expect.objectContaining({ kind: 'MEDICAL', code: 'MEDICAL-IT', status: 'ACTIVE' }))

    await wrapper.vm.transition(wrapper.vm.bookings[0], 'IN_PROGRESS', '开始医疗服务')
    expect(transitionServiceBooking).toHaveBeenCalledWith('b-1', { status: 'IN_PROGRESS', note: '开始医疗服务', version: 1 })
  })
})

function flush() {
  return new Promise(resolve => setTimeout(resolve, 0))
}
