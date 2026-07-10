import { createLocalVue, shallowMount } from '@vue/test-utils'
import VueRouter from 'vue-router'
import PageAtmosphere from '@/components/ui/PageAtmosphere.vue'
import AiAssistantBubble from '@/components/ui/AiAssistantBubble.vue'

const localVue = createLocalVue()
localVue.use(VueRouter)
const originalRequestAnimationFrame = window.requestAnimationFrame
const originalCancelAnimationFrame = window.cancelAnimationFrame
const originalMatchMedia = window.matchMedia

describe('ambient UI components', () => {
  beforeEach(() => {
    jest.useFakeTimers()
    window.requestAnimationFrame = jest.fn((callback) => {
      window.__sceneFrame = callback
      return 1
    })
    window.cancelAnimationFrame = jest.fn()
    window.matchMedia = jest.fn(() => ({ matches: false }))
  })

  afterEach(() => {
    jest.useRealTimers()
    window.requestAnimationFrame = originalRequestAnimationFrame
    window.cancelAnimationFrame = originalCancelAnimationFrame
    window.matchMedia = originalMatchMedia
    delete window.__sceneFrame
  })

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

  it('crossfades old and new scene layers before retiring the old background', async () => {
    const wrapper = shallowMount(PageAtmosphere, {
      localVue,
      propsData: { scene: 'care' }
    })

    await wrapper.setProps({ scene: 'ai' })
    expect(wrapper.findAll('[data-testid="scene-layer"]')).toHaveLength(2)
    expect(wrapper.find('[data-scene-layer="care"]').classes()).toContain('is-active')

    window.__sceneFrame()
    await wrapper.vm.$nextTick()
    expect(wrapper.find('[data-scene-layer="ai"]').classes()).toContain('is-active')

    jest.advanceTimersByTime(700)
    await wrapper.vm.$nextTick()
    expect(wrapper.findAll('[data-testid="scene-layer"]')).toHaveLength(1)
    expect(wrapper.find('[data-scene-layer="ai"]').exists()).toBe(true)
  })

  it('finishes a crossfade on transitionend without waiting for the fallback timer', async () => {
    const wrapper = shallowMount(PageAtmosphere, { localVue, propsData: { scene: 'care' } })
    await wrapper.setProps({ scene: 'ai' })
    window.__sceneFrame()
    await wrapper.vm.$nextTick()

    await wrapper.find('[data-scene-layer="ai"]').trigger('transitionend', { propertyName: 'opacity' })

    expect(wrapper.findAll('[data-testid="scene-layer"]')).toHaveLength(1)
    expect(wrapper.find('[data-scene-layer="ai"]').exists()).toBe(true)
  })

  it('cancels an obsolete frame when routes change rapidly', async () => {
    const wrapper = shallowMount(PageAtmosphere, { localVue, propsData: { scene: 'care' } })
    await wrapper.setProps({ scene: 'ai' })
    const obsoleteFrame = window.__sceneFrame

    await wrapper.setProps({ scene: 'service' })
    expect(window.cancelAnimationFrame).toHaveBeenCalledWith(1)
    expect(window.__sceneFrame).not.toBe(obsoleteFrame)
    expect(wrapper.find('[data-scene-layer="ai"]').exists()).toBe(false)

    window.__sceneFrame()
    await wrapper.vm.$nextTick()
    expect(wrapper.find('[data-scene-layer="service"]').classes()).toContain('is-active')
  })

  it('does not enable pointer parallax when reduced motion is requested', () => {
    window.matchMedia = jest.fn(() => ({ matches: true }))
    const addEventListener = jest.spyOn(window, 'addEventListener')

    const wrapper = shallowMount(PageAtmosphere, { localVue, propsData: { scene: 'care' } })

    expect(addEventListener).not.toHaveBeenCalledWith('pointermove', expect.any(Function), expect.anything())
    addEventListener.mockRestore()
    wrapper.destroy()
  })

  it('offers a clear AI entry without trapping pointer events around it', () => {
    const wrapper = shallowMount(AiAssistantBubble, { localVue })

    expect(wrapper.attributes('aria-label')).toContain('AI')
    expect(wrapper.find('router-link-stub').attributes('to')).toBe('/ai/chat')
    expect(wrapper.text()).toContain('问问派派')
  })
})
