import { createLocalVue, mount, shallowMount } from '@vue/test-utils'
import VueRouter from 'vue-router'
import navigation from '@/navigation'
import AppIcon from '@/components/ui/AppIcon.vue'
import BrandMark from '@/components/ui/BrandMark.vue'
import PublicLayout from '@/layouts/PublicLayout.vue'
import AdminLayout from '@/layouts/AdminLayout.vue'

const localVue = createLocalVue()
localVue.use(VueRouter)

describe('PetSpark brand and navigation icon system', () => {
  test('every visible navigation entry declares a semantic icon', () => {
    const entries = [...navigation.publicNav, ...navigation.memberNav, ...navigation.adminNav]

    expect(entries.length).toBeGreaterThan(20)
    expect(entries.every((entry) => typeof entry.icon === 'string' && entry.icon.length > 0)).toBe(true)
    entries.forEach((entry) => {
      const icon = shallowMount(AppIcon, { propsData: { name: entry.icon } })
      expect(icon.findAll('path').length).toBeGreaterThan(0)
    })
  })

  test('does not silently replace an unknown icon with the pet symbol', () => {
    const wrapper = shallowMount(AppIcon, { propsData: { name: 'misspelled-icon' } })

    expect(wrapper.findAll('path')).toHaveLength(0)
  })

  test('renders the original brand image instead of a text placeholder', () => {
    const wrapper = mount(BrandMark, { propsData: { tagline: '认真照顾每一次陪伴' } })

    expect(wrapper.find('img[alt=""]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="brand-symbol"]').attributes('src')).toMatch(/petspark-mark\.svg$/)
    expect(wrapper.text()).toContain('PetSpark')
    expect(wrapper.text()).not.toContain('派')
  })

  test('renders accessible icons alongside user and admin navigation labels', () => {
    const router = new VueRouter()
    const publicWrapper = shallowMount(PublicLayout, {
      localVue,
      router,
      propsData: {
        isAuthenticated: true,
        publicNav: [{ to: '/', text: '首页', icon: 'home' }],
        memberNav: [{ to: 'pets', text: '宠物', icon: 'pets' }]
      }
    })
    const adminWrapper = shallowMount(AdminLayout, {
      localVue,
      router,
      propsData: { adminNav: [{ to: 'admin-users', text: '用户管理', icon: 'users' }] }
    })

    expect(publicWrapper.findAllComponents(AppIcon).length).toBeGreaterThanOrEqual(2)
    expect(adminWrapper.findAllComponents(AppIcon).length).toBe(1)
    expect(publicWrapper.findComponent(BrandMark).exists()).toBe(true)
    expect(adminWrapper.findComponent(BrandMark).props('inverse')).toBe(true)
  })
})
