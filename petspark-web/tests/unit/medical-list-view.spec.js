jest.mock('@/api/service', () => ({
  listServiceItems: jest.fn()
}))

import { shallowMount } from '@vue/test-utils'
import MedicalListView from '@/views/MedicalListView.vue'
import { listServiceItems } from '@/api/service'

describe('MedicalListView', () => {
  beforeEach(() => {
    jest.clearAllMocks()
    listServiceItems.mockResolvedValue({
      data: {
        items: [
          { id: 'medical-1', kind: 'MEDICAL', name: '基础医疗', basePrice: 128, medicalProfile: { careScope: '健康体检' } }
        ],
        page: 1, size: 12, total: 1
      }
    })
  })

  it('loads active MEDICAL service items on mount', async () => {
    const wrapper = shallowMount(MedicalListView, {
      mocks: { $message: { error: jest.fn() } },
      stubs: ['el-card', 'el-input', 'el-button', 'router-link']
    })
    await flush()

    expect(listServiceItems).toHaveBeenCalledWith({
      kind: 'MEDICAL', status: 'ACTIVE', keyword: undefined, page: 1, size: 12
    })
    expect(wrapper.vm.items).toHaveLength(1)
  })
})

function flush() {
  return new Promise(resolve => setTimeout(resolve, 0))
}
