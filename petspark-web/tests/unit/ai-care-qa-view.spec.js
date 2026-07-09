jest.mock('@/api/ai', () => ({
  getAiStatus: jest.fn(),
  getCareQaStatus: jest.fn(),
  grantAiConsent: jest.fn(),
  withdrawAiConsent: jest.fn(),
  createAiConversation: jest.fn(),
  sendCareQaMessage: jest.fn(),
  deleteAiConversation: jest.fn(),
  listAiMessages: jest.fn()
}))

import { createLocalVue, shallowMount } from '@vue/test-utils'
import Vuex from 'vuex'
import ElementUI from 'element-ui'
import AiCareQaView from '@/views/AiCareQaView.vue'
import {
  getAiStatus,
  getCareQaStatus,
  createAiConversation,
  sendCareQaMessage,
  listAiMessages,
  deleteAiConversation,
  withdrawAiConsent
} from '@/api/ai'

function mountView() {
  const localVue = createLocalVue()
  localVue.use(Vuex)
  localVue.use(ElementUI)
  const store = new Vuex.Store({
    state: { accessToken: 't', user: { nickname: 'tester' } },
    getters: { isAuthenticated: () => true }
  })
  return shallowMount(AiCareQaView, {
    localVue,
    store,
    mocks: { $message: { success: jest.fn(), error: jest.fn(), warning: jest.fn() } },
    stubs: {
      'el-button': true,
      'el-alert': true,
      'el-input': true,
      'el-icon': true,
      'el-dialog': true,
      'el-checkbox': true,
      'el-tag': true,
      'router-link': true,
      // 保留真实 StatusPanel 以便其 data-testid 属性渲染到 DOM，供选择器查找。
      StatusPanel: false
    }
  })
}

describe('AiCareQaView', () => {
  beforeEach(() => {
    getAiStatus.mockReset()
    getCareQaStatus.mockReset()
    createAiConversation.mockReset()
    sendCareQaMessage.mockReset()
    listAiMessages.mockReset()
    deleteAiConversation.mockReset()
    withdrawAiConsent.mockReset()
  })

  it('loads global + care-qa status on mount and shows degradation banner when scene disabled', async () => {
    getAiStatus.mockResolvedValue({
      data: { enabled: true, consentGranted: true, degradationReason: '' }
    })
    getCareQaStatus.mockResolvedValue({
      data: { enabled: false, scene: 'CARE_QA' }
    })

    const wrapper = mountView()
    await flushPromises()

    expect(getAiStatus).toHaveBeenCalled()
    expect(getCareQaStatus).toHaveBeenCalled()
    expect(wrapper.find('[data-testid="care-qa-degradation-banner"]').exists()).toBe(true)
    expect(wrapper.vm.careQaEnabled).toBe(false)
  })

  it('shows degradation banner when global AI is disabled even if care-qa flag on', async () => {
    getAiStatus.mockResolvedValue({
      data: { enabled: false, consentGranted: false, degradationReason: 'AI gateway is disabled' }
    })
    getCareQaStatus.mockResolvedValue({
      data: { enabled: true, scene: 'CARE_QA' }
    })

    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.find('[data-testid="care-qa-degradation-banner"]').exists()).toBe(true)
    expect(wrapper.vm.careQaEnabled).toBe(false)
  })

  it('shows consent-required panel when care-qa enabled but not consented', async () => {
    getAiStatus.mockResolvedValue({
      data: { enabled: true, consentGranted: false, degradationReason: '' }
    })
    getCareQaStatus.mockResolvedValue({
      data: { enabled: true, scene: 'CARE_QA' }
    })

    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.find('[status="empty"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="show-consent"]').exists()).toBe(true)
  })

  it('creates a CARE_QA conversation and selects it when enabled and consented', async () => {
    getAiStatus.mockResolvedValue({
      data: { enabled: true, consentGranted: true, degradationReason: '' }
    })
    getCareQaStatus.mockResolvedValue({
      data: { enabled: true, scene: 'CARE_QA' }
    })
    createAiConversation.mockResolvedValue({
      data: { id: 'c-9', scene: 'CARE_QA', title: '护理问答 1', status: 'ACTIVE' }
    })
    listAiMessages.mockResolvedValue({ data: [] })

    const wrapper = mountView()
    await flushPromises()

    wrapper.find('[data-testid="new-conversation"]').vm.$emit('click')
    await flushPromises()

    expect(createAiConversation).toHaveBeenCalledWith(expect.objectContaining({ scene: 'CARE_QA' }))
    expect(wrapper.vm.currentId).toBe('c-9')
  })

  it('sends a care-qa message and renders structured reply (URGENT highlights seek-help)', async () => {
    getAiStatus.mockResolvedValue({
      data: { enabled: true, consentGranted: true, degradationReason: '' }
    })
    getCareQaStatus.mockResolvedValue({
      data: { enabled: true, scene: 'CARE_QA' }
    })
    createAiConversation.mockResolvedValue({ data: { id: 'c-9', scene: 'CARE_QA', title: 't', status: 'ACTIVE' } })
    listAiMessages.mockResolvedValue({ data: [] })
    sendCareQaMessage.mockResolvedValue({
      data: {
        requestId: 'r-1',
        riskLevel: 'URGENT',
        generalAdvice: ['立即保暖', '观察呼吸'],
        warningSigns: ['呼吸困难', '黏膜发紫'],
        seekHelp: '请立即前往宠物急诊或联系兽医',
        disclaimer: '本回复不构成兽医诊断，紧急情况请立即就医。',
        usage: {}
      }
    })

    const wrapper = mountView()
    await flushPromises()
    wrapper.find('[data-testid="new-conversation"]').vm.$emit('click')
    await flushPromises()

    wrapper.setData({ text: '猫咪呼吸困难' })
    wrapper.find('[data-testid="message-input"]').vm.$emit('input', '猫咪呼吸困难')
    await flushPromises()

    wrapper.find('[data-testid="send-message"]').vm.$emit('click')
    await flushPromises()

    expect(sendCareQaMessage).toHaveBeenCalledWith('c-9', '猫咪呼吸困难')
    const last = wrapper.vm.messages[wrapper.vm.messages.length - 1]
    expect(last.role).toBe('assistant')
    expect(last.payload.riskLevel).toBe('URGENT')
    expect(last.payload.seekHelp).toContain('宠物急诊')
    expect(last.payload.disclaimer).toContain('不构成兽医诊断')
  })

  it('parses assistant history content as JSON payload', async () => {
    getAiStatus.mockResolvedValue({
      data: { enabled: true, consentGranted: true, degradationReason: '' }
    })
    getCareQaStatus.mockResolvedValue({
      data: { enabled: true, scene: 'CARE_QA' }
    })
    createAiConversation.mockResolvedValue({ data: { id: 'c-9', scene: 'CARE_QA', title: 't', status: 'ACTIVE' } })
    listAiMessages.mockResolvedValue({
      data: [
        { id: 'm-1', role: 'user', content: '狗狗呕吐' },
        {
          id: 'm-2',
          role: 'assistant',
          content: JSON.stringify({
            riskLevel: 'ATTENTION',
            generalAdvice: ['禁食 6 小时'],
            warningSigns: ['持续呕吐'],
            seekHelp: '若 24 小时未改善请就医',
            disclaimer: '本回复不构成兽医诊断。'
          })
        }
      ]
    })

    const wrapper = mountView()
    await flushPromises()
    wrapper.find('[data-testid="new-conversation"]').vm.$emit('click')
    await flushPromises()

    expect(wrapper.vm.messages.length).toBe(2)
    const assistant = wrapper.vm.messages.find((m) => m.role === 'assistant')
    expect(assistant.payload.riskLevel).toBe('ATTENTION')
    expect(assistant.payload.generalAdvice).toContain('禁食 6 小时')
  })

  it('deletes a conversation', async () => {
    getAiStatus.mockResolvedValue({
      data: { enabled: true, consentGranted: true, degradationReason: '' }
    })
    getCareQaStatus.mockResolvedValue({
      data: { enabled: true, scene: 'CARE_QA' }
    })
    createAiConversation.mockResolvedValue({ data: { id: 'c-9', scene: 'CARE_QA', title: 't', status: 'ACTIVE' } })
    listAiMessages.mockResolvedValue({ data: [] })
    deleteAiConversation.mockResolvedValue({})

    const wrapper = mountView()
    await flushPromises()
    wrapper.find('[data-testid="new-conversation"]').vm.$emit('click')
    await flushPromises()

    wrapper.find('[data-testid="delete-session-c-9"]').vm.$emit('click', { stopPropagation: () => {} })
    await flushPromises()

    expect(deleteAiConversation).toHaveBeenCalledWith('c-9')
    expect(wrapper.vm.currentId).toBe('')
  })

  it('revokes consent', async () => {
    getAiStatus.mockResolvedValue({
      data: { enabled: true, consentGranted: true, degradationReason: '' }
    })
    getCareQaStatus.mockResolvedValue({
      data: { enabled: true, scene: 'CARE_QA' }
    })
    withdrawAiConsent.mockResolvedValue({})

    const wrapper = mountView()
    await flushPromises()

    wrapper.find('[data-testid="revoke-consent"]').vm.$emit('click')
    await flushPromises()

    expect(withdrawAiConsent).toHaveBeenCalled()
    expect(wrapper.vm.consentGranted).toBe(false)
  })

  it('riskLabel maps URGENT/ATTENTION/GENERAL to Chinese labels', () => {
    const wrapper = mountView()
    expect(wrapper.vm.riskLabel('URGENT')).toBe('紧急')
    expect(wrapper.vm.riskLabel('ATTENTION')).toBe('需关注')
    expect(wrapper.vm.riskLabel('GENERAL')).toBe('一般观察')
  })

  it('riskTagType maps URGENT/ATTENTION/GENERAL to element-ui tag types', () => {
    const wrapper = mountView()
    expect(wrapper.vm.riskTagType('URGENT')).toBe('danger')
    expect(wrapper.vm.riskTagType('ATTENTION')).toBe('warning')
    expect(wrapper.vm.riskTagType('GENERAL')).toBe('success')
  })
})

function flushPromises() {
  return new Promise((resolve) => setTimeout(resolve, 0))
}
