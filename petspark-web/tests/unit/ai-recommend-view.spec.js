jest.mock('@/api/ai', () => ({
  getAiStatus: jest.fn(),
  withdrawAiConsent: jest.fn(),
  recommendAi: jest.fn()
}))

import { createLocalVue, shallowMount } from '@vue/test-utils'
import Vuex from 'vuex'
import ElementUI from 'element-ui'
import AiRecommendView from '@/views/AiRecommendView.vue'
import { getAiStatus, withdrawAiConsent, recommendAi } from '@/api/ai'

function mountView() {
  const localVue = createLocalVue()
  localVue.use(Vuex)
  localVue.use(ElementUI)
  const store = new Vuex.Store({
    state: { accessToken: 't', user: { nickname: 'tester' } },
    getters: { isAuthenticated: () => true }
  })
  return shallowMount(AiRecommendView, {
    localVue,
    store,
    mocks: { $message: { success: jest.fn(), error: jest.fn(), warning: jest.fn() } },
    stubs: {
      'el-button': true,
      'el-alert': true,
      'el-input': true,
      'el-input-number': true,
      'el-select': true,
      'el-option': true,
      'el-form': true,
      'el-form-item': true,
      'el-dialog': true,
      'el-checkbox': true,
      'router-link': true,
      StatusPanel: false
    }
  })
}

describe('AiRecommendView', () => {
  beforeEach(() => {
    getAiStatus.mockReset()
    withdrawAiConsent.mockReset()
    recommendAi.mockReset()
  })

  it('loads status on mount and shows consent-required panel when not consented', async () => {
    getAiStatus.mockResolvedValue({
      data: { enabled: true, consentGranted: false, degradationReason: '' }
    })
    const wrapper = mountView()
    await flushPromises()
    expect(getAiStatus).toHaveBeenCalled()
    expect(wrapper.find('[data-testid="show-consent"]').exists()).toBe(true)
  })

  it('shows degradation banner when AI disabled but consent granted', async () => {
    getAiStatus.mockResolvedValue({
      data: { enabled: false, consentGranted: true, degradationReason: 'AI gateway is disabled' }
    })
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.find('[data-testid="ai-degradation-banner"]').exists()).toBe(true)
    // 表单仍可见（规则兜底可用）
    expect(wrapper.find('[data-testid="recommend-submit"]').exists()).toBe(true)
  })

  it('submits recommendation and renders result items', async () => {
    getAiStatus.mockResolvedValue({
      data: { enabled: false, consentGranted: true, degradationReason: 'AI gateway is disabled' }
    })
    recommendAi.mockResolvedValue({
      data: {
        requestId: 'r-1',
        items: [
          { id: 'g-1', type: 'GOODS', reason: '规则推荐：分类:玩具，有货' },
          { id: 'g-2', type: 'GOODS', reason: '规则推荐：分类:主食，有货' }
        ],
        usage: { promptTokens: 0, completionTokens: 0, totalTokens: 0 },
        boundaryNotice: '【提示】AI 回复不构成兽医诊断。'
      }
    })

    const wrapper = mountView()
    await flushPromises()

    wrapper.setData({
      form: { species: '狗', age: 36, preference: '活泼', candidateType: 'GOODS' }
    })
    await flushPromises()

    wrapper.find('[data-testid="recommend-submit"]').vm.$emit('click')
    await flushPromises()

    expect(recommendAi).toHaveBeenCalledWith({
      species: '狗',
      age: 36,
      preference: '活泼',
      candidateType: 'GOODS'
    })
    expect(wrapper.find('[data-testid="recommend-result"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="recommend-item-0"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="recommend-boundary"]').exists()).toBe(true)
  })

  it('renders empty state when no candidates', async () => {
    getAiStatus.mockResolvedValue({
      data: { enabled: false, consentGranted: true, degradationReason: '' }
    })
    recommendAi.mockResolvedValue({
      data: { requestId: 'r-2', items: [], usage: {}, boundaryNotice: '' }
    })
    const wrapper = mountView()
    await flushPromises()

    wrapper.setData({ form: { species: '狗', age: 0, preference: 'test', candidateType: 'PET' } })
    await flushPromises()

    wrapper.find('[data-testid="recommend-submit"]').vm.$emit('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="recommend-empty"]').exists()).toBe(true)
  })

  it('renders error state on API failure', async () => {
    getAiStatus.mockResolvedValue({
      data: { enabled: true, consentGranted: true, degradationReason: '' }
    })
    recommendAi.mockRejectedValue(new Error('AI 服务未启用或未配置'))
    const wrapper = mountView()
    await flushPromises()

    wrapper.setData({ form: { species: '猫', age: 12, preference: '温顺', candidateType: 'SERVICE' } })
    await flushPromises()

    wrapper.find('[data-testid="recommend-submit"]').vm.$emit('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="recommend-error"]').exists()).toBe(true)
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
