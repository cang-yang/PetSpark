jest.mock('@/api/stray', () => ({
  assignStrayClue: jest.fn(),
  getAdminStrayClue: jest.fn(),
  listAdminStrayClues: jest.fn(),
  transitionStrayClue: jest.fn()
}))

import { shallowMount } from '@vue/test-utils'
import AdminStrayCluesView from '@/views/AdminStrayCluesView.vue'
import { assignStrayClue, getAdminStrayClue, listAdminStrayClues, transitionStrayClue } from '@/api/stray'

describe('AdminStrayCluesView', () => {
  beforeEach(() => {
    jest.clearAllMocks()
    listAdminStrayClues.mockResolvedValue({
      data: { items: [{ id: 's-1', clueNo: 'STRAY-1', animalType: 'CAT', location: '东门', status: 'SUBMITTED', version: 0 }], total: 1 }
    })
    getAdminStrayClue.mockResolvedValue({
      data: { id: 's-1', clueNo: 'STRAY-1', reporterUserId: 'u-1', animalType: 'CAT', location: '东门', description: '橘猫', status: 'SUBMITTED', version: 0 }
    })
    assignStrayClue.mockResolvedValue({ data: { id: 's-1', status: 'ASSIGNED', version: 1 } })
    transitionStrayClue.mockResolvedValue({ data: { id: 's-1', status: 'RESOLVED', version: 2 } })
  })

  it('loads admin clues on mount', async () => {
    const wrapper = mountView()
    await flush()

    expect(listAdminStrayClues).toHaveBeenCalledWith({ keyword: undefined, status: undefined, page: 1, size: 20 })
    expect(wrapper.vm.clues[0].clueNo).toBe('STRAY-1')
  })

  it('opens admin detail', async () => {
    const wrapper = mountView()
    await flush()

    await wrapper.vm.openDetail({ id: 's-1' })

    expect(getAdminStrayClue).toHaveBeenCalledWith('s-1')
    expect(wrapper.vm.current.reporterUserId).toBe('u-1')
    expect(wrapper.vm.showDetail).toBe(true)
  })

  it('assigns clue with version', async () => {
    const wrapper = mountView()
    await flush()

    wrapper.vm.openAssign({ id: 's-1', assignedUserId: '', adminNote: '', version: 0 })
    wrapper.vm.assignForm.assignedUserId = 'u-admin'
    wrapper.vm.assignForm.note = '安排救助'
    await wrapper.vm.submitAssign()

    expect(assignStrayClue).toHaveBeenCalledWith('s-1', { assignedUserId: 'u-admin', note: '安排救助', version: 0 })
    expect(wrapper.vm.showAssign).toBe(false)
  })

  it('transitions clue with handoff placeholder', async () => {
    const wrapper = mountView()
    await flush()

    wrapper.vm.openTransition({ id: 's-1', adminNote: '', handoffPetId: '', handoffNote: '', version: 1 }, 'RESOLVED')
    wrapper.vm.transitionForm.handoffNote = '后续可建档领养'
    await wrapper.vm.submitTransition()

    expect(transitionStrayClue).toHaveBeenCalledWith('s-1', {
      status: 'RESOLVED',
      note: '',
      handoffPetId: undefined,
      handoffNote: '后续可建档领养',
      version: 1
    })
  })
})

function mountView() {
  return shallowMount(AdminStrayCluesView, {
    mocks: { $message: { success: jest.fn(), error: jest.fn(), warning: jest.fn() } },
    stubs: ['el-table', 'el-table-column', 'el-tag', 'el-button', 'el-select', 'el-option', 'el-dialog', 'el-input', 'el-form', 'el-form-item']
  })
}

function flush() {
  return new Promise(resolve => setTimeout(resolve, 0))
}
