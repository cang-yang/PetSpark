jest.mock('@/api/adoption', () => ({
  listMyAdoptions: jest.fn(),
  getAdoption: jest.fn(),
  withdrawAdoption: jest.fn()
}))

import { shallowMount } from '@vue/test-utils'
import MyAdoptionsView from '@/views/MyAdoptionsView.vue'
import { getAdoption, listMyAdoptions, withdrawAdoption } from '@/api/adoption'

describe('MyAdoptionsView', () => {
  beforeEach(() => {
    jest.clearAllMocks()
    listMyAdoptions.mockResolvedValue({
      data: {
        items: [
          { id: 'a-1', applicationNo: 'ADOPT-1', petName: '小白', status: 'PENDING', createdAt: '2026-07-08T09:00:00Z', version: 1 }
        ],
        page: 1, size: 10, total: 1
      }
    })
    getAdoption.mockResolvedValue({
      data: {
        id: 'a-1', applicationNo: 'ADOPT-1', status: 'APPROVED',
        statusLabel: '已通过', statusClass: 'info', role: '申请人', nextStep: '等待交接',
        petName: '小白', pet: { name: '小白' },
        statement: '我希望领养', version: 2
      }
    })
    withdrawAdoption.mockResolvedValue({
      data: { id: 'a-1', status: 'WITHDRAWN', statusLabel: '已撤回', statusClass: 'info', version: 3 }
    })
  })

  it('loads my adoptions on mount', async () => {
    const wrapper = shallowMount(MyAdoptionsView, {
      mocks: { $message: { success: jest.fn(), error: jest.fn(), warning: jest.fn() } },
      stubs: ['el-table', 'el-table-column', 'el-tag', 'el-button', 'el-select', 'el-option', 'el-dialog', 'el-input', 'StatusPanel']
    })
    await flush()

    expect(listMyAdoptions).toHaveBeenCalledWith({ status: undefined, page: 1, size: 10 })
    expect(wrapper.vm.applications).toHaveLength(1)
    expect(wrapper.vm.applications[0].applicationNo).toBe('ADOPT-1')
  })

  it('opens detail and withdraws application with version', async () => {
    const wrapper = shallowMount(MyAdoptionsView, {
      mocks: { $message: { success: jest.fn(), error: jest.fn(), warning: jest.fn() } },
      stubs: ['el-table', 'el-table-column', 'el-tag', 'el-button', 'el-select', 'el-option', 'el-dialog', 'el-input', 'StatusPanel']
    })
    await flush()

    await wrapper.vm.openDetail({ id: 'a-1' })
    expect(getAdoption).toHaveBeenCalledWith('a-1')
    expect(wrapper.vm.current.id).toBe('a-1')
    expect(wrapper.vm.current.status).toBe('APPROVED')

    wrapper.vm.withdrawReason = '暂时不方便'
    await wrapper.vm.submitWithdraw()
    expect(withdrawAdoption).toHaveBeenCalledWith('a-1', { reason: '暂时不方便', version: 2 })
  })
})

function flush() {
  return new Promise(resolve => setTimeout(resolve, 0))
}
