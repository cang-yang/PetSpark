jest.mock('@/api/adoption', () => ({
  listAdminAdoptions: jest.fn(),
  getAdoption: jest.fn(),
  decideAdoption: jest.fn(),
  handoverAdoption: jest.fn()
}))

import { shallowMount } from '@vue/test-utils'
import AdminAdoptionsView from '@/views/AdminAdoptionsView.vue'
import { decideAdoption, getAdoption, handoverAdoption, listAdminAdoptions } from '@/api/adoption'

describe('AdminAdoptionsView', () => {
  beforeEach(() => {
    jest.clearAllMocks()
    listAdminAdoptions.mockResolvedValue({
      data: {
        items: [
          { id: 'a-1', applicationNo: 'ADOPT-1', applicantUsername: 'alice', petName: '小白', status: 'PENDING', createdAt: '2026-07-08T09:00:00Z', version: 1 }
        ],
        page: 1, size: 10, total: 1
      }
    })
    getAdoption.mockResolvedValue({
      data: {
        id: 'a-1', applicationNo: 'ADOPT-1', status: 'PENDING',
        statusLabel: '待审核', statusClass: 'warning', role: '管理员', nextStep: '请审核申请',
        applicantUsername: 'alice', petName: '小白', pet: { name: '小白' },
        statement: '我希望领养', version: 1
      }
    })
    decideAdoption.mockResolvedValue({
      data: { id: 'a-1', status: 'APPROVED', statusLabel: '已通过', statusClass: 'info', role: '管理员', nextStep: '等待交接', version: 2 }
    })
    handoverAdoption.mockResolvedValue({
      data: { id: 'a-1', status: 'COMPLETED', statusLabel: '已完成', statusClass: 'success', role: '申请人', nextStep: '领养完成', version: 3 }
    })
  })

  it('loads admin adoptions on mount', async () => {
    const wrapper = shallowMount(AdminAdoptionsView, {
      mocks: { $message: { success: jest.fn(), error: jest.fn(), warning: jest.fn() } },
      stubs: ['el-table', 'el-table-column', 'el-tag', 'el-button', 'el-select', 'el-option', 'el-dialog', 'el-input', 'StatusPanel']
    })
    await flush()

    expect(listAdminAdoptions).toHaveBeenCalledWith({ keyword: undefined, status: undefined, page: 1, size: 10 })
    expect(wrapper.vm.applications).toHaveLength(1)
    expect(wrapper.vm.applications[0].applicationNo).toBe('ADOPT-1')
  })

  it('approves pending application with version', async () => {
    const wrapper = shallowMount(AdminAdoptionsView, {
      mocks: { $message: { success: jest.fn(), error: jest.fn(), warning: jest.fn() } },
      stubs: ['el-table', 'el-table-column', 'el-tag', 'el-button', 'el-select', 'el-option', 'el-dialog', 'el-input', 'StatusPanel']
    })
    await flush()

    await wrapper.vm.openDetail({ id: 'a-1' })
    expect(wrapper.vm.current.status).toBe('PENDING')

    wrapper.vm.decisionNote = '条件合适'
    await wrapper.vm.submitDecision('APPROVED')
    expect(decideAdoption).toHaveBeenCalledWith('a-1', { decision: 'APPROVED', note: '条件合适', version: 1 })
    expect(wrapper.vm.current.status).toBe('APPROVED')
  })

  it('rejects pending application and requires note', async () => {
    const $message = { success: jest.fn(), error: jest.fn(), warning: jest.fn() }
    const wrapper = shallowMount(AdminAdoptionsView, {
      mocks: { $message },
      stubs: ['el-table', 'el-table-column', 'el-tag', 'el-button', 'el-select', 'el-option', 'el-dialog', 'el-input', 'StatusPanel']
    })
    await flush()

    await wrapper.vm.openDetail({ id: 'a-1' })
    wrapper.vm.decisionNote = ''
    await wrapper.vm.submitDecision('REJECTED')
    expect(decideAdoption).not.toHaveBeenCalled()
    expect($message.warning).toHaveBeenCalledWith('驳回请填写审核意见')

    wrapper.vm.decisionNote = '条件不满足'
    await wrapper.vm.submitDecision('REJECTED')
    expect(decideAdoption).toHaveBeenCalledWith('a-1', { decision: 'REJECTED', note: '条件不满足', version: 1 })
  })

  it('records handover success for approved application', async () => {
    const wrapper = shallowMount(AdminAdoptionsView, {
      mocks: { $message: { success: jest.fn(), error: jest.fn(), warning: jest.fn() } },
      stubs: ['el-table', 'el-table-column', 'el-tag', 'el-button', 'el-select', 'el-option', 'el-dialog', 'el-input', 'StatusPanel']
    })
    await flush()

    getAdoption.mockResolvedValueOnce({
      data: {
        id: 'a-1', applicationNo: 'ADOPT-1', status: 'APPROVED',
        statusLabel: '已通过', statusClass: 'info', role: '管理员', nextStep: '等待交接',
        applicantUsername: 'alice', petName: '小白', pet: { name: '小白' },
        statement: '我希望领养', version: 2
      }
    })
    await wrapper.vm.openDetail({ id: 'a-1' })
    expect(wrapper.vm.current.status).toBe('APPROVED')

    wrapper.vm.handoverResult = 'SUCCESS'
    wrapper.vm.handoverNote = '已交接'
    await wrapper.vm.submitHandover()
    expect(handoverAdoption).toHaveBeenCalledWith('a-1', { result: 'SUCCESS', note: '已交接', version: 2 })
    expect(wrapper.vm.current.status).toBe('COMPLETED')
  })
})

function flush() {
  return new Promise(resolve => setTimeout(resolve, 0))
}
