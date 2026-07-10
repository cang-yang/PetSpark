jest.mock('@/api/pets', () => ({ listAdminPets: jest.fn() }))
jest.mock('@/api/orders', () => ({
  listAdminOrders: jest.fn(),
  transitionOrder: jest.fn(),
  cancelOrder: jest.fn()
}))

import { createLocalVue, shallowMount } from '@vue/test-utils'
import AdminPetsView from '@/views/AdminPetsView.vue'
import AdminOrdersView from '@/views/AdminOrdersView.vue'
import { listAdminPets } from '@/api/pets'
import { cancelOrder, listAdminOrders } from '@/api/orders'

const localVue = createLocalVue()
localVue.directive('loading', {})

describe('admin console page experience', () => {
  beforeEach(() => {
    jest.clearAllMocks()
    listAdminPets.mockResolvedValue({ data: { data: { items: [], total: 0 } } })
    listAdminOrders.mockResolvedValue({ data: { items: [], total: 0 } })
    cancelOrder.mockResolvedValue({ data: { id: 'o-1', status: 'CANCELLED', version: 2 } })
  })

  it('uses the shared admin header and table shell for pet management', async () => {
    const wrapper = shallowMount(AdminPetsView, {
      localVue,
      mocks: { $message: { error: jest.fn() } },
      stubs: ['el-button', 'el-table', 'el-table-column', 'el-pagination']
    })
    await flush()

    expect(wrapper.findComponent({ name: 'AdminPageHeader' }).exists()).toBe(true)
    expect(wrapper.findComponent({ name: 'AdminTableShell' }).exists()).toBe(true)
    expect(wrapper.find('table').exists()).toBe(false)
  })

  it('opens a shared confirmation dialog before cancelling an order', async () => {
    const wrapper = shallowMount(AdminOrdersView, {
      localVue,
      mocks: { $message: { success: jest.fn(), error: jest.fn() } },
      stubs: ['el-button', 'el-input', 'el-select', 'el-option', 'el-table', 'el-table-column', 'el-pagination']
    })
    await flush()
    const row = { id: 'o-1', status: 'CREATED', version: 1 }

    wrapper.vm.requestCancel(row)
    expect(wrapper.vm.cancelDialogVisible).toBe(true)
    expect(wrapper.findComponent({ name: 'ConfirmActionDialog' }).exists()).toBe(true)

    await wrapper.vm.confirmCancel('用户申请取消')
    expect(cancelOrder).toHaveBeenCalledWith('o-1', { reason: '用户申请取消', version: 1 })
  })
})

function flush() {
  return new Promise(resolve => setTimeout(resolve, 0))
}
