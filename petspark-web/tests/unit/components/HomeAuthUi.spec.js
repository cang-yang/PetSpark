import { createLocalVue, shallowMount } from '@vue/test-utils'
import VueRouter from 'vue-router'
import PageHero from '@/components/ui/PageHero.vue'
import FeatureCard from '@/components/ui/FeatureCard.vue'
import AuthPanel from '@/components/ui/AuthPanel.vue'

const localVue = createLocalVue()
localVue.use(VueRouter)
const router = new VueRouter()

describe('home and authentication UI components', () => {
  it('renders a hero with primary and secondary actions', () => {
    const wrapper = shallowMount(PageHero, {
      localVue,
      router,
      propsData: {
        title: '让每一次陪伴，都被认真照顾',
        description: '把宠物生活管理得更安心。',
        primaryText: '开始管理我的宠物',
        primaryTo: '/my/pets',
        secondaryText: '看看可领养宠物',
        secondaryTo: '/adoptions'
      }
    })

    expect(wrapper.get('h1').text()).toContain('每一次陪伴')
    expect(wrapper.findAll('router-link-stub')).toHaveLength(2)
  })

  it('turns a platform capability into a navigable feature card', () => {
    const wrapper = shallowMount(FeatureCard, {
      localVue,
      router,
      propsData: {
        title: '我的宠物',
        description: '管理档案与健康记录',
        icon: '宠',
        to: '/my/pets',
        tone: 'pink'
      }
    })

    expect(wrapper.text()).toContain('我的宠物')
    expect(wrapper.attributes('data-tone')).toBe('pink')
  })

  it('provides a shared two-column authentication panel', () => {
    const wrapper = shallowMount(AuthPanel, {
      localVue,
      propsData: {
        title: '欢迎回来',
        description: '继续照顾你牵挂的小伙伴。',
        imageSrc: '/login-dog.jpg',
        imageAlt: '阳光下安静陪伴主人的狗狗'
      },
      slots: { default: '<form data-testid="auth-form">表单</form>' }
    })

    expect(wrapper.get('img').attributes('alt')).toBe('阳光下安静陪伴主人的狗狗')
    expect(wrapper.find('[data-testid="auth-form"]').exists()).toBe(true)
  })
})
