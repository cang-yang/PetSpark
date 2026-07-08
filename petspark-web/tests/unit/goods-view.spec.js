import { shallowMount } from '@vue/test-utils'
import GoodsListView from '@/views/GoodsListView.vue'
import GoodsDetailView from '@/views/GoodsDetailView.vue'
import { getGoods, listGoods } from '@/api/catalog'

jest.mock('@/api/catalog', () => ({
  getGoods: jest.fn(),
  listGoods: jest.fn()
}))

describe('Goods views', () => {
  beforeEach(() => jest.clearAllMocks())

  it('loads published goods list', async () => {
    listGoods.mockResolvedValue({
      data: { items: [{ id: 'g-1', name: '猫粮', sku: 'CAT-FOOD', price: 19.9, stock: 8 }], page: 1, size: 12, total: 1 }
    })
    const wrapper = shallowMount(GoodsListView, {
      mocks: { $message: { error: jest.fn() } },
      stubs: ['router-link', 'el-card', 'el-input', 'el-button', 'el-pagination']
    })
    await flush()

    expect(listGoods).toHaveBeenCalledWith({ keyword: undefined, categoryId: undefined, page: 1, size: 12 })
    expect(wrapper.vm.goods[0].sku).toBe('CAT-FOOD')
  })

  it('loads goods detail by route id', async () => {
    getGoods.mockResolvedValue({ data: { id: 'g-1', name: '猫抓板', sku: 'CAT-BOARD', stock: 3, price: 29.9 } })
    const wrapper = shallowMount(GoodsDetailView, {
      mocks: { $route: { params: { id: 'g-1' } }, $message: { error: jest.fn() } },
      stubs: ['el-card']
    })
    await flush()

    expect(getGoods).toHaveBeenCalledWith('g-1')
    expect(wrapper.vm.goods.name).toBe('猫抓板')
  })
})

function flush() {
  return new Promise(resolve => setTimeout(resolve, 0))
}
