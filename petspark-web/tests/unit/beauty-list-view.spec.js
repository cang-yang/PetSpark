jest.mock('@/api/service', () => ({
  listServiceItems: jest.fn()
}))

import { shallowMount } from '@vue/test-utils'
import BeautyListView from '@/views/BeautyListView.vue'
import { listServiceItems } from '@/api/service'

describe('BeautyListView', () => {
  beforeEach(() => {
    jest.clearAllMocks()
    listServiceItems.mockResolvedValue({
      data: {
        items: [
          { id: 'beauty-1', kind: 'BEAUTY', name: '基础美容', basePrice: 128, beautyProfile: { carePreferences: '温和洗护' } }
        ],
        page: 1, size: 12, total: 1
      }
    })
  })

  it('loads active BEAUTY service items on mount', async () => {
    const wrapper = shallowMount(BeautyListView, {
      mocks: { $message: { error: jest.fn() } },
      stubs: ['el-card', 'el-input', 'el-button', 'router-link']
    })
    await flush()

    expect(listServiceItems).toHaveBeenCalledWith({
      kind: 'BEAUTY', status: 'ACTIVE', keyword: undefined, page: 1, size: 12
    })
    expect(wrapper.vm.items).toHaveLength(1)
  })
})

function flush() {
  return new Promise(resolve => setTimeout(resolve, 0))
}
