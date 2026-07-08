jest.mock('@/api/orders', () => ({
  listAdminOrders: jest.fn(),
  transitionOrder: jest.fn(),
  cancelOrder: jest.fn()
}))

import { shallowMount } from '@vue/test-utils'
import AdminOrdersView from '@/views/AdminOrdersView.vue'
import { cancelOrder, listAdminOrders, transitionOrder } from '@/api/orders'

describe('AdminOrdersView', () => {
  beforeEach(() => {
    jest.clearAllMocks()
    listAdminOrders.mockResolvedValue({
      data: {
        items: [
          { id: 'o-1', orderNo: 'ORD-1', userId: 'u-1', status: 'CREATED', totalAmount: 12.00, createdAt: '2026-07-08T09:00:00Z', version: 1 }
        ],
        page: 1, size: 10, total: 1
      }
    })
    transitionOrder.mockResolvedValue({ data: { id: 'o-1', status: 'PROCESSING', version: 2 } })
    cancelOrder.mockResolvedValue({ data: { id: 'o-1', status: 'CANCELLED', version: 2 } })
  })

  it('loads admin orders on mount', async () => {
    const wrapper = shallowMount(AdminOrdersView, {
      mocks: { $message: { success: jest.fn(), error: jest.fn() } },
      stubs: ['el-table', 'el-table-column', 'el-tag', 'el-button', 'el-input', 'el-select', 'el-option']
    })
    await flush()

    expect(listAdminOrders).toHaveBeenCalledWith({ keyword: undefined, status: undefined, page: 1, size: 10 })
    expect(wrapper.vm.orders).toHaveLength(1)
  })

  it('transitions order state with version', async () => {
    const wrapper = shallowMount(AdminOrdersView, {
      mocks: { $message: { success: jest.fn(), error: jest.fn() } },
      stubs: ['el-table', 'el-table-column', 'el-tag', 'el-button', 'el-input', 'el-select', 'el-option']
    })
    await flush()

    await wrapper.vm.transition(wrapper.vm.orders[0], 'PROCESSING', '开始处理')
    expect(transitionOrder).toHaveBeenCalledWith('o-1', { status: 'PROCESSING', note: '开始处理', version: 1 })
  })
})

function flush() {
  return new Promise(resolve => setTimeout(resolve, 0))
}
