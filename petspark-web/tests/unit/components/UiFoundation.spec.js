import { createLocalVue, shallowMount } from '@vue/test-utils'
import ElementUI from 'element-ui'
import EmptyState from '@/components/ui/EmptyState.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import LoadingState from '@/components/ui/LoadingState.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import MetricCard from '@/components/ui/MetricCard.vue'
import FilterBar from '@/components/ui/FilterBar.vue'

const localVue = createLocalVue()
localVue.use(ElementUI)

describe('UI foundation components', () => {
  it('turns an empty result into a guided next action', () => {
    const wrapper = shallowMount(EmptyState, {
      localVue,
      propsData: {
        title: '还没有宠物档案',
        description: '添加第一只宠物后，就能记录健康与日常。',
        actionText: '添加宠物'
      }
    })

    expect(wrapper.text()).toContain('还没有宠物档案')
    expect(wrapper.get('[data-testid="empty-state-action"]').text()).toBe('添加宠物')
  })

  it('offers a retry action for recoverable errors', async () => {
    const wrapper = shallowMount(ErrorState, {
      localVue,
      propsData: { description: '网络连接不稳定，请重试。' }
    })

    wrapper.findComponent({ name: 'ElButton' }).vm.$emit('click')
    await wrapper.vm.$nextTick()
    expect(wrapper.emitted('retry')).toHaveLength(1)
  })

  it('announces loading state to assistive technology', () => {
    const wrapper = shallowMount(LoadingState, { localVue })
    expect(wrapper.attributes('aria-busy')).toBe('true')
    expect(wrapper.attributes('aria-live')).toBe('polite')
  })

  it('renders a page heading with optional actions', () => {
    const wrapper = shallowMount(PageHeader, {
      localVue,
      propsData: { title: '我的宠物', description: '集中管理宠物档案与健康记录。' },
      slots: { actions: '<button data-testid="add-pet">添加宠物</button>' }
    })

    expect(wrapper.get('h1').text()).toBe('我的宠物')
    expect(wrapper.find('[data-testid="add-pet"]').exists()).toBe(true)
  })

  it('renders metric values and preserves filter controls through slots', () => {
    const metric = shallowMount(MetricCard, {
      localVue,
      propsData: { label: '宠物总数', value: 28, hint: '较上周新增 3 只' }
    })
    const filter = shallowMount(FilterBar, {
      localVue,
      slots: { default: '<input data-testid="keyword" />' }
    })

    expect(metric.text()).toContain('28')
    expect(metric.text()).toContain('较上周新增 3 只')
    expect(filter.find('[data-testid="keyword"]').exists()).toBe(true)
  })
})
