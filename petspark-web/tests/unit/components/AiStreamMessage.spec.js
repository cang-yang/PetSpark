import { mount } from '@vue/test-utils'
import AiStreamMessage from '@/components/AiStreamMessage.vue'

describe('AiStreamMessage', () => {
  it('shows accumulated delta content before the stream finishes', async () => {
    const wrapper = mount(AiStreamMessage)

    wrapper.vm.reset()
    wrapper.vm.onDelta({ content: '第一段 **重点**' })
    await wrapper.vm.$nextTick()

    expect(wrapper.vm.status).toBe('streaming')
    expect(wrapper.get('[data-testid="stream-content"]').text()).toContain('第一段')
    expect(wrapper.get('[data-testid="stream-content"]').html()).toContain('<strong>重点</strong>')
  })
})
