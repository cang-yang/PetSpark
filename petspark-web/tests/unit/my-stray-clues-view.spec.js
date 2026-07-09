jest.mock('@/api/stray', () => ({
  getMyStrayClue: jest.fn(),
  listMyStrayClues: jest.fn()
}))

import { shallowMount } from '@vue/test-utils'
import MyStrayCluesView from '@/views/MyStrayCluesView.vue'
import { getMyStrayClue, listMyStrayClues } from '@/api/stray'

describe('MyStrayCluesView', () => {
  beforeEach(() => {
    jest.clearAllMocks()
    listMyStrayClues.mockResolvedValue({
      data: { items: [{ id: 's-1', clueNo: 'STRAY-1', animalType: 'CAT', location: '东门', status: 'SUBMITTED', version: 0 }], total: 1 }
    })
    getMyStrayClue.mockResolvedValue({
      data: { id: 's-1', clueNo: 'STRAY-1', animalType: 'CAT', location: '东门', description: '橘猫', status: 'SUBMITTED', images: [] }
    })
  })

  it('loads member clues on mount', async () => {
    const wrapper = mountView()
    await flush()

    expect(listMyStrayClues).toHaveBeenCalledWith({ status: undefined, page: 1, size: 10 })
    expect(wrapper.vm.clues).toHaveLength(1)
    expect(wrapper.vm.clues[0].clueNo).toBe('STRAY-1')
  })

  it('opens detail through member detail endpoint', async () => {
    const wrapper = mountView()
    await flush()

    await wrapper.vm.openDetail({ id: 's-1' })

    expect(getMyStrayClue).toHaveBeenCalledWith('s-1')
    expect(wrapper.vm.current.description).toBe('橘猫')
    expect(wrapper.vm.showDetail).toBe(true)
  })
})

function mountView() {
  return shallowMount(MyStrayCluesView, {
    mocks: { $message: { success: jest.fn(), error: jest.fn(), warning: jest.fn() } },
    stubs: ['el-table', 'el-table-column', 'el-tag', 'el-button', 'el-select', 'el-option', 'el-dialog']
  })
}

function flush() {
  return new Promise(resolve => setTimeout(resolve, 0))
}
