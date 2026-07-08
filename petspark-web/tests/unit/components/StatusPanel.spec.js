import { shallowMount, createLocalVue } from '@vue/test-utils'
import StatusPanel from '@/components/StatusPanel.vue'

describe('StatusPanel', () => {
  function mount(props = {}, slots = {}) {
    const localVue = createLocalVue()
    return shallowMount(StatusPanel, { localVue, propsData: props, slots })
  }

  it('renders status label, reason, role and next step', () => {
    const wrapper = mount({
      status: 'blocked',
      statusLabel: '审核驳回',
      statusClass: 'warning',
      reason: '图片不符合规范',
      role: '内容运营',
      nextStep: '修改图片后重新提交'
    })

    expect(wrapper.text()).toContain('审核驳回')
    expect(wrapper.text()).toContain('图片不符合规范')
    expect(wrapper.text()).toContain('内容运营')
    expect(wrapper.text()).toContain('修改图片后重新提交')
  })

  it('exposes an actions slot for allowed operations', () => {
    const wrapper = mount(
      { status: 'blocked', statusLabel: '审核驳回' },
      { actions: '<button data-testid="retry">重试</button>' }
    )

    expect(wrapper.find('[data-testid="retry"]').exists()).toBe(true)
  })

  it('omits role and next step when not provided', () => {
    const wrapper = mount({ status: 'ok', statusLabel: '正常' })
    expect(wrapper.text()).toContain('正常')
    expect(wrapper.text()).not.toContain('责任角色')
  })
})
