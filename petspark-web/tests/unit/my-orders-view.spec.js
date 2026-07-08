jest.mock('@/api/orders', () => ({
  listMyOrders: jest.fn(),
  getOrder: jest.fn(),
  cancelOrder: jest.fn()
}))

import { shallowMount } from '@vue/test-utils'
import MyOrdersView from '@/views/MyOrdersView.vue'
import { cancelOrder, getOrder, listMyOrders } from '@/api/orders'

describe('MyOrdersView', () => {
  beforeEach(() => {
    jest.clearAllMocks()
    listMyOrders.mockResolvedValue({
      data: {
        items: [
          { id: 'o-1', orderNo: 'ORD-1', status: 'CREATED', totalAmount: 12.00, createdAt: '2026-07-08T09:00:00Z', version: 1 }
        ],
        page: 1, size: 10, total: 1
      }
    })
    getOrder.mockResolvedValue({
      data: {
        id: 'o-1', orderNo: 'ORD-1', status: 'CREATED', totalAmount: 12.00,
        recipientName: '张三', recipientPhone: '13800000000', address: '北京市',
        items: [{ sku: 'S-1', name: '商品', unitPrice: 6.00, quantity: 2, lineAmount: 12.00 }],
        version: 1
      }
    })
    cancelOrder.mockResolvedValue({ data: { id: 'o-1', status: 'CANCELLED', version: 2 } })
  })

  it('loads my orders on mount', async () => {
    const wrapper = shallowMount(MyOrdersView, {
      mocks: { $message: { success: jest.fn(), error: jest.fn(), warning: jest.fn() } },
      stubs: ['el-table', 'el-table-column', 'el-tag', 'el-button', 'el-select', 'el-option', 'el-dialog', 'el-input']
    })
    await flush()

    expect(listMyOrders).toHaveBeenCalledWith({ status: undefined, page: 1, size: 10 })
    expect(wrapper.vm.orders).toHaveLength(1)
    expect(wrapper.vm.orders[0].orderNo).toBe('ORD-1')
  })

  it('opens detail and cancels order with version', async () => {
    const wrapper = shallowMount(MyOrdersView, {
      mocks: { $message: { success: jest.fn(), error: jest.fn(), warning: jest.fn() } },
      stubs: ['el-table', 'el-table-column', 'el-tag', 'el-button', 'el-select', 'el-option', 'el-dialog', 'el-input']
    })
    await flush()

    await wrapper.vm.openDetail({ id: 'o-1' })
    expect(getOrder).toHaveBeenCalledWith('o-1')
    expect(wrapper.vm.current.id).toBe('o-1')

    wrapper.vm.cancelReason = '不想要了'
    await wrapper.vm.submitCancel()
    expect(cancelOrder).toHaveBeenCalledWith('o-1', { reason: '不想要了', version: 1 })
  })
})

function flush() {
  return new Promise(resolve => setTimeout(resolve, 0))
}
