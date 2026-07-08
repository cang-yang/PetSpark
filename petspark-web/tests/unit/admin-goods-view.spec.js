import { shallowMount } from '@vue/test-utils'
import AdminGoodsView from '@/views/AdminGoodsView.vue'
import { adjustGoodsStock, createGoods, listAdminGoods, updateGoodsStatus } from '@/api/catalog'

jest.mock('@/api/catalog', () => ({
  adjustGoodsStock: jest.fn(),
  createGoods: jest.fn(),
  listAdminGoods: jest.fn(),
  updateGoodsStatus: jest.fn()
}))

describe('AdminGoodsView', () => {
  beforeEach(() => {
    jest.clearAllMocks()
    listAdminGoods.mockResolvedValue({
      data: { items: [{ id: 'g-1', sku: 'CAT-1', name: '猫粮', status: 'DRAFT', stock: 5, version: 1 }], page: 1, size: 10, total: 1 }
    })
    createGoods.mockResolvedValue({ data: { id: 'g-2', sku: 'CAT-2' } })
    updateGoodsStatus.mockResolvedValue({ data: { id: 'g-1', status: 'ACTIVE', version: 2 } })
    adjustGoodsStock.mockResolvedValue({ data: { id: 'g-1', stock: 8, version: 2 } })
  })

  it('loads backend goods and sends versioned actions', async () => {
    const wrapper = shallowMount(AdminGoodsView, {
      mocks: { $message: { success: jest.fn(), error: jest.fn() } },
      stubs: ['el-card', 'el-button', 'el-input', 'el-table', 'el-table-column', 'el-select', 'el-option', 'el-dialog', 'el-form', 'el-form-item']
    })
    await flush()

    expect(listAdminGoods).toHaveBeenCalledWith({ keyword: undefined, status: undefined, page: 1, size: 10 })

    await wrapper.vm.changeStatus(wrapper.vm.goods[0], 'ACTIVE')
    expect(updateGoodsStatus).toHaveBeenCalledWith('g-1', { status: 'ACTIVE', version: 1 })

    await wrapper.vm.adjustStock(wrapper.vm.goods[0], 3, '补货')
    expect(adjustGoodsStock).toHaveBeenCalledWith('g-1', { delta: 3, reason: '补货', version: 2 })
  })
})

function flush() {
  return new Promise(resolve => setTimeout(resolve, 0))
}
