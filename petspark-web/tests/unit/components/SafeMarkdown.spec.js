import { shallowMount } from '@vue/test-utils'
import SafeMarkdown from '@/components/ui/SafeMarkdown.vue'

describe('SafeMarkdown', () => {
  it('renders emphasis, lists and paragraphs', () => {
    const wrapper = shallowMount(SafeMarkdown, {
      propsData: { content: '先观察\n\n- **进食状态**\n- `精神状态`' }
    })

    expect(wrapper.html()).toContain('<strong>进食状态</strong>')
    expect(wrapper.html()).toContain('<ul>')
    expect(wrapper.html()).toContain('<code>精神状态</code>')
  })

  it('escapes raw html instead of executing it', () => {
    const wrapper = shallowMount(SafeMarkdown, {
      propsData: { content: '<img src=x onerror=alert(1)> **安全**' }
    })

    expect(wrapper.find('img').exists()).toBe(false)
    expect(wrapper.html()).toContain('&lt;img')
    expect(wrapper.html()).toContain('<strong>安全</strong>')
  })
})
