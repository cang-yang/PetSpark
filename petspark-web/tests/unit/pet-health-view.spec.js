import { shallowMount } from '@vue/test-utils'
import PetHealthView from '@/views/PetHealthView.vue'
import { listHealthRecords, createHealthRecord, reviseHealthRecord, eraseHealthRecord } from '@/api/health'

jest.mock('@/api/health', () => ({
  listHealthRecords: jest.fn(),
  createHealthRecord: jest.fn(),
  reviseHealthRecord: jest.fn(),
  eraseHealthRecord: jest.fn()
}))

const flush = () => new Promise(resolve => setTimeout(resolve, 0))

describe('PetHealthView', () => {
  beforeEach(() => {
    jest.clearAllMocks()
    listHealthRecords.mockResolvedValue({
      data: {
        items: [
          { id: 'rec-1', recordType: 'VACCINATION', occurredOn: '2026-01-02', summary: '狂犬疫苗', status: 'ACTIVE', detail: '批次A123', version: 0 }
        ],
        page: 1,
        size: 20,
        total: 1
      }
    })
    createHealthRecord.mockResolvedValue({ data: { id: 'rec-2' } })
    reviseHealthRecord.mockResolvedValue({ data: { id: 'rec-3' } })
    eraseHealthRecord.mockResolvedValue({ data: null })
  })

  it('loads health records on created', async () => {
    const wrapper = shallowMount(PetHealthView, {
      mocks: { $message: { success: jest.fn(), error: jest.fn() }, $route: { params: { id: 'pet-1' } } },
      stubs: ['el-card', 'el-button', 'el-input', 'el-select', 'el-option', 'el-date-picker', 'el-form', 'el-form-item', 'el-tag', 'el-dialog']
    })
    await flush()
    await wrapper.vm.$nextTick()

    expect(listHealthRecords).toHaveBeenCalledWith('pet-1', { page: 1, size: 20, recordType: undefined })
    expect(wrapper.vm.records[0].summary).toBe('狂犬疫苗')
    expect(wrapper.text()).toContain('狂犬疫苗')
  })

  it('creates a new health record then reloads', async () => {
    const wrapper = shallowMount(PetHealthView, {
      mocks: { $message: { success: jest.fn(), error: jest.fn() }, $route: { params: { id: 'pet-1' } } },
      stubs: ['el-card', 'el-button', 'el-input', 'el-select', 'el-option', 'el-date-picker', 'el-form', 'el-form-item', 'el-tag', 'el-dialog']
    })
    await flush()

    wrapper.vm.form.recordType = 'CHECKUP'
    wrapper.vm.form.occurredOn = '2026-03-01'
    wrapper.vm.form.summary = '年度体检'
    wrapper.vm.form.detail = '一切正常'
    await wrapper.vm.submitCreate()
    await flush()

    expect(createHealthRecord).toHaveBeenCalledWith('pet-1', expect.objectContaining({
      recordType: 'CHECKUP',
      occurredOn: '2026-03-01',
      summary: '年度体检',
      detail: '一切正常'
    }))
  })

  it('erases a record with a reason', async () => {
    const wrapper = shallowMount(PetHealthView, {
      mocks: { $message: { success: jest.fn(), error: jest.fn() }, $route: { params: { id: 'pet-1' } } },
      stubs: ['el-card', 'el-button', 'el-input', 'el-select', 'el-option', 'el-date-picker', 'el-form', 'el-form-item', 'el-tag', 'el-dialog']
    })
    await flush()

    window.prompt = jest.fn(() => '主体撤回授权')
    await wrapper.vm.erase(wrapper.vm.records[0])
    await flush()

    expect(eraseHealthRecord).toHaveBeenCalledWith('rec-1', { reason: '主体撤回授权' })
  })
})