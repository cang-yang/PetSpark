import { shallowMount } from '@vue/test-utils'
import PublicLayout from '@/layouts/PublicLayout.vue'

const RouterLinkStub = {
  name: 'RouterLink',
  props: ['to'],
  render(h) {
    return h('a', { attrs: { href: typeof this.to === 'string' ? this.to : '#' } }, this.$slots.default)
  }
}

function mountLayout() {
  return shallowMount(PublicLayout, {
    propsData: {
      isAuthenticated: true,
      publicNav: [{ to: '/', text: '首页', icon: 'home' }],
      memberNav: [{ to: '/goods', text: '商品', icon: 'goods' }],
      adminNav: []
    },
    mocks: { $route: { path: '/', fullPath: '/' } },
    stubs: {
      RouterLink: RouterLinkStub,
      AppIcon: true,
      BrandMark: true,
      AiAssistantBubble: true,
      transition: false
    }
  })
}

describe('PublicLayout function menu', () => {
  it('toggles from the trigger and closes on outside pointer down', async () => {
    const wrapper = mountLayout()
    const trigger = wrapper.find('[data-testid="nav-menu-trigger"]')

    await trigger.trigger('click')
    expect(wrapper.vm.navMenuOpen).toBe(true)
    expect(trigger.attributes('aria-expanded')).toBe('true')

    await trigger.trigger('click')
    expect(wrapper.vm.navMenuOpen).toBe(false)

    await trigger.trigger('click')
    document.body.dispatchEvent(new Event('pointerdown', { bubbles: true }))
    await wrapper.vm.$nextTick()
    expect(wrapper.vm.navMenuOpen).toBe(false)
    wrapper.destroy()
  })

  it('closes on Escape and on a route change', async () => {
    const wrapper = mountLayout()
    await wrapper.find('[data-testid="nav-menu-trigger"]').trigger('click')

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    await wrapper.vm.$nextTick()
    expect(wrapper.vm.navMenuOpen).toBe(false)

    wrapper.vm.navMenuOpen = true
    wrapper.vm.$options.watch['$route.fullPath'].call(wrapper.vm)
    expect(wrapper.vm.navMenuOpen).toBe(false)
    wrapper.destroy()
  })

  it('closes after choosing a destination and when authentication ends', async () => {
    const wrapper = mountLayout()
    await wrapper.find('[data-testid="nav-menu-trigger"]').trigger('click')
    await wrapper.find('.nav-menu__panel a').trigger('click')
    expect(wrapper.vm.navMenuOpen).toBe(false)

    wrapper.vm.navMenuOpen = true
    wrapper.vm.$options.watch.isAuthenticated.call(wrapper.vm, false)
    expect(wrapper.vm.navMenuOpen).toBe(false)
    wrapper.destroy()
  })
})
