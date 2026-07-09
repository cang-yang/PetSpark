jest.mock('@/api/stray', () => ({
  createStrayClue: jest.fn()
}))

jest.mock('@/components/ImageUploader.vue', () => ({
  name: 'ImageUploader',
  props: ['value', 'businessType'],
  render(h) { return h('div') }
}))

import { shallowMount } from '@vue/test-utils'
import StrayCluesView from '@/views/StrayCluesView.vue'
import { createStrayClue } from '@/api/stray'

describe('StrayCluesView', () => {
  beforeEach(() => {
    jest.clearAllMocks()
    createStrayClue.mockResolvedValue({
      data: { id: 's-1', clueNo: 'STRAY-20260709-0001', status: 'SUBMITTED', statusLabel: '待受理' }
    })
  })

  it('requires location and description before submitting', async () => {
    const $message = { success: jest.fn(), error: jest.fn(), warning: jest.fn() }
    const wrapper = mountView($message)

    await wrapper.vm.submit()

    expect(createStrayClue).not.toHaveBeenCalled()
    expect($message.warning).toHaveBeenCalledWith('请填写发现位置')
  })

  it('confirms images then creates stray clue with idempotency key', async () => {
    const $message = { success: jest.fn(), error: jest.fn(), warning: jest.fn() }
    const wrapper = mountView($message)
    wrapper.setData({
      form: { animalType: 'CAT', location: '东门花坛', description: '橘猫疑似受伤', contactPhone: '13800000000' }
    })
    wrapper.vm.$refs.uploaders = [
      { value: 'f-1', confirm: jest.fn().mockResolvedValue({ fileId: 'f-1' }) },
      { value: '', confirm: jest.fn() }
    ]

    await wrapper.vm.submit()

    expect(createStrayClue).toHaveBeenCalledWith({
      animalType: 'CAT',
      location: '东门花坛',
      description: '橘猫疑似受伤',
      contactPhone: '13800000000',
      imageFileIds: ['f-1']
    }, expect.stringMatching(/^stray-/))
    expect(wrapper.vm.created.clueNo).toBe('STRAY-20260709-0001')
    expect($message.success).toHaveBeenCalledWith('救助线索已提交')
  })
})

function mountView($message) {
  return shallowMount(StrayCluesView, {
    mocks: { $message },
    stubs: ['el-form', 'el-form-item', 'el-select', 'el-option', 'el-input', 'el-button', 'el-alert', 'ImageUploader']
  })
}
