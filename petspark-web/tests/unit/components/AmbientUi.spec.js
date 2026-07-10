import { createLocalVue, shallowMount } from '@vue/test-utils'
import VueRouter from 'vue-router'
import PageAtmosphere from '@/components/ui/PageAtmosphere.vue'
import AiAssistantBubble from '@/components/ui/AiAssistantBubble.vue'

const localVue = createLocalVue()
localVue.use(VueRouter)

describe('ambient UI components', () => {
  it('renders a decorative, non-interactive scene layer', () => {
    const wrapper = shallowMount(PageAtmosphere, {
      localVue,
      propsData: { scene: 'ai' }
    })

    expect(wrapper.attributes('aria-hidden')).toBe('true')
    expect(wrapper.attributes('data-scene')).toBe('ai')
    expect(wrapper.classes()).toContain('page-atmosphere--ai')
    expect(wrapper.find('[data-testid="scene-artwork"]').exists()).toBe(true)
  })

  it('offers a clear AI entry without trapping pointer events around it', () => {
    const wrapper = shallowMount(AiAssistantBubble, { localVue })

    expect(wrapper.attributes('aria-label')).toContain('AI')
    expect(wrapper.find('router-link-stub').attributes('to')).toBe('/ai/chat')
    expect(wrapper.text()).toContain('问问派派')
  })
})
