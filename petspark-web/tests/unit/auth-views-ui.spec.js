import { shallowMount } from '@vue/test-utils'
import LoginView from '@/views/LoginView.vue'
import RegisterView from '@/views/RegisterView.vue'
import ForgotPasswordView from '@/views/ForgotPasswordView.vue'
import { issueCaptcha } from '@/api/auth'

jest.mock('@/api/auth', () => ({
  issueCaptcha: jest.fn(),
  login: jest.fn(),
  register: jest.fn(),
  requestPasswordResetCode: jest.fn(),
  resetPassword: jest.fn()
}))

describe('authentication page presentation', () => {
  beforeEach(() => {
    issueCaptcha.mockReturnValue(new Promise(() => {}))
  })

  it.each([
    [LoginView, '欢迎回来'],
    [RegisterView, '创建你的 PetSpark 账号'],
    [ForgotPasswordView, '找回访问权限']
  ])('uses the shared branded authentication panel', (component, title) => {
    const wrapper = shallowMount(component, {
      stubs: ['el-form', 'el-form-item', 'el-input', 'el-button', 'el-alert', 'el-steps', 'el-step', 'router-link']
    })

    const panel = wrapper.findComponent({ name: 'AuthPanel' })
    expect(panel.exists()).toBe(true)
    expect(panel.props('title')).toBe(title)
  })
})
