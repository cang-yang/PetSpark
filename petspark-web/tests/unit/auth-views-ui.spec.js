import { shallowMount } from '@vue/test-utils'
import LoginView from '@/views/LoginView.vue'
import RegisterView from '@/views/RegisterView.vue'
import ForgotPasswordView from '@/views/ForgotPasswordView.vue'
import { issueCaptcha, login, requestRegistrationCode } from '@/api/auth'

jest.mock('@/api/auth', () => ({
  issueCaptcha: jest.fn(),
  login: jest.fn(),
  register: jest.fn(),
  requestRegistrationCode: jest.fn(),
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

  it('returns to the protected banner target after a successful login', async () => {
    issueCaptcha.mockResolvedValue({ data: { captchaId: 'captcha-1', challengeText: '1 + 1 = ?' } })
    login.mockResolvedValue({ data: { accessToken: 'token', user: { nickname: '演示用户' } } })
    const dispatch = jest.fn().mockResolvedValue()
    const push = jest.fn()
    const wrapper = shallowMount(LoginView, {
      mocks: {
        $store: { dispatch },
        $router: { push },
        $route: { query: { redirect: '/boarding/new' } },
        $message: { success: jest.fn() }
      },
      stubs: ['el-form', 'el-form-item', 'el-input', 'el-button', 'el-alert', 'router-link']
    })
    await flush()
    Object.assign(wrapper.vm.form, { principal: 'demo', password: 'not-a-real-password', captchaAnswer: '2' })

    await wrapper.vm.submit()

    expect(dispatch).toHaveBeenCalledWith('saveLogin', expect.any(Object))
    expect(push).toHaveBeenCalledWith('/boarding/new')
  })

  it('rejects a protocol-relative login redirect', async () => {
    issueCaptcha.mockResolvedValue({ data: { captchaId: 'captcha-1', challengeText: '1 + 1 = ?' } })
    login.mockResolvedValue({ data: { accessToken: 'token', user: { nickname: '演示用户' } } })
    const push = jest.fn()
    const wrapper = shallowMount(LoginView, {
      mocks: {
        $store: { dispatch: jest.fn().mockResolvedValue() },
        $router: { push },
        $route: { query: { redirect: '//malicious.example' } },
        $message: { success: jest.fn() }
      },
      stubs: ['el-form', 'el-form-item', 'el-input', 'el-button', 'el-alert', 'router-link']
    })
    await flush()
    await wrapper.vm.submit()

    expect(push).toHaveBeenCalledWith('/')
  })

  it('requests a registration code with email and arithmetic captcha', async () => {
    issueCaptcha.mockResolvedValue({ data: { captchaId: 'captcha-1', challengeText: '1 + 1 = ?' } })
    requestRegistrationCode.mockResolvedValue({})
    const wrapper = shallowMount(RegisterView, {
      mocks: { $message: { success: jest.fn() } },
      stubs: ['el-form', 'el-form-item', 'el-input', 'el-button', 'el-alert', 'router-link']
    })
    await flush()
    Object.assign(wrapper.vm.form, { email: 'USER@Example.com', captchaAnswer: '2' })

    await wrapper.vm.sendEmailCode()

    expect(requestRegistrationCode).toHaveBeenCalledWith({
      email: 'USER@Example.com',
      captchaId: 'captcha-1',
      captchaAnswer: '2'
    })
    expect(wrapper.vm.emailCodeCooldown).toBe(60)
    wrapper.destroy()
  })
})

function flush() {
  return new Promise(resolve => setTimeout(resolve, 0))
}
