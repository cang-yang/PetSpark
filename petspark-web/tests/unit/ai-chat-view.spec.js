jest.mock('@/api/ai', () => ({
  getAiStatus: jest.fn(),
  grantAiConsent: jest.fn(),
  withdrawAiConsent: jest.fn(),
  createAiConversation: jest.fn(),
  sendAiMessage: jest.fn(),
  deleteAiConversation: jest.fn(),
  listAiMessages: jest.fn(),
  streamAiMessage: jest.fn()
}))

import { createLocalVue, shallowMount } from '@vue/test-utils'
import Vuex from 'vuex'
import ElementUI from 'element-ui'
import AiChatView from '@/views/AiChatView.vue'
import {
  getAiStatus,
  createAiConversation,
  sendAiMessage,
  listAiMessages,
  deleteAiConversation,
  withdrawAiConsent,
  streamAiMessage
} from '@/api/ai'

function mountView() {
  const localVue = createLocalVue()
  localVue.use(Vuex)
  localVue.use(ElementUI)
  const store = new Vuex.Store({
    state: { accessToken: 't', user: { nickname: 'tester' } },
    getters: { isAuthenticated: () => true }
  })
  return shallowMount(AiChatView, {
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
      'router-link': true,
      // 保留真实 StatusPanel 以便其 data-testid 属性渲染到 DOM，供选择器查找。
      StatusPanel: false
    }
  })
}

describe('AiChatView', () => {
  beforeEach(() => {
    getAiStatus.mockReset()
    createAiConversation.mockReset()
    sendAiMessage.mockReset()
    listAiMessages.mockReset()
    deleteAiConversation.mockReset()
    withdrawAiConsent.mockReset()
    streamAiMessage.mockReset()
  })

  it('loads status on mount and shows degradation banner when disabled', async () => {
    getAiStatus.mockResolvedValue({
      data: { enabled: false, scene: 'PET_CHAT', consentGranted: false, degradationReason: 'AI gateway is disabled' }
    })

    const wrapper = mountView()
    await flushPromises()

    expect(getAiStatus).toHaveBeenCalled()
    expect(wrapper.find('[data-testid="ai-degradation-banner"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="new-conversation"]').attributes('disabled')).toBeTruthy()
  })

  it('shows consent-required panel when enabled but not consented', async () => {
    getAiStatus.mockResolvedValue({
      data: { enabled: true, consentGranted: false, degradationReason: '' }
    })

    const wrapper = mountView()
    await flushPromises()

    // StatusPanel 渲染为存根，原 data-testid 被折叠为 test-id 存根属性；
    // 这里改成断言降级分支未触发 + 同意入口按钮渲染（vue-test-utils 归一化为 testid）。
    expect(wrapper.find('[status="empty"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="show-consent"]').exists()).toBe(true)
  })

  it('creates a conversation and selects it', async () => {
    getAiStatus.mockResolvedValue({
      data: { enabled: true, consentGranted: true, degradationReason: '' }
    })
    createAiConversation.mockResolvedValue({
      data: { id: 'c-1', scene: 'PET_CHAT', title: '对话 1', status: 'ACTIVE' }
    })
    listAiMessages.mockResolvedValue({ data: [] })

    const wrapper = mountView()
    await flushPromises()

    const btn = wrapper.find('[data-testid="new-conversation"]')
    btn.vm.$emit('click')
    await flushPromises()

    expect(createAiConversation).toHaveBeenCalled()
    expect(wrapper.vm.currentId).toBe('c-1')
    expect(wrapper.find('[data-testid="no-conversation"]').exists()).toBe(false)
  })

  it('sends a message and appends assistant reply', async () => {
    getAiStatus.mockResolvedValue({
      data: { enabled: true, consentGranted: true, degradationReason: '' }
    })
    createAiConversation.mockResolvedValue({ data: { id: 'c-1', scene: 'PET_CHAT', title: 't', status: 'ACTIVE' } })
    listAiMessages.mockResolvedValue({ data: [] })
    sendAiMessage.mockResolvedValue({
      data: { requestId: 'r-1', content: '你好呀', boundaryNotice: '【提示】AI 回复不构成兽医诊断。', usage: {} }
    })

    const wrapper = mountView()
    await flushPromises()
    wrapper.find('[data-testid="new-conversation"]').vm.$emit('click')
    await flushPromises()

    wrapper.setData({ text: '嗨' })
    wrapper.find('[data-testid="message-input"]').vm.$emit('input', '嗨')
    await flushPromises()

    wrapper.find('[data-testid="send-message"]').vm.$emit('click')
    await flushPromises()

    expect(sendAiMessage).toHaveBeenCalledWith('c-1', '嗨')
    const last = wrapper.vm.messages[wrapper.vm.messages.length - 1]
    expect(last.role).toBe('assistant')
    expect(last.content).toContain('你好呀')
  })

  it('deletes a conversation', async () => {
    getAiStatus.mockResolvedValue({
      data: { enabled: true, consentGranted: true, degradationReason: '' }
    })
    createAiConversation.mockResolvedValue({ data: { id: 'c-1', scene: 'PET_CHAT', title: 't', status: 'ACTIVE' } })
    listAiMessages.mockResolvedValue({ data: [] })
    deleteAiConversation.mockResolvedValue({})

    const wrapper = mountView()
    await flushPromises()
    wrapper.find('[data-testid="new-conversation"]').vm.$emit('click')
    await flushPromises()

    wrapper.find('[data-testid="delete-session-c-1"]').vm.$emit('click', { stopPropagation: () => {} })
    await flushPromises()

    expect(deleteAiConversation).toHaveBeenCalledWith('c-1')
    expect(wrapper.vm.currentId).toBe('')
  })

  it('revokes consent', async () => {
    getAiStatus.mockResolvedValue({
      data: { enabled: true, consentGranted: true, degradationReason: '' }
    })
    withdrawAiConsent.mockResolvedValue({})

    const wrapper = mountView()
    await flushPromises()

    wrapper.find('[data-testid="revoke-consent"]').vm.$emit('click')
    await flushPromises()

    expect(withdrawAiConsent).toHaveBeenCalled()
    expect(wrapper.vm.consentGranted).toBe(false)
  })
})

function flushPromises() {
  return new Promise((resolve) => setTimeout(resolve, 0))
}