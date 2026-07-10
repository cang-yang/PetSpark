import { createLocalVue, shallowMount } from '@vue/test-utils'
import ElementUI from 'element-ui'
import AdminPageHeader from '@/components/ui/AdminPageHeader.vue'
import AdminTableShell from '@/components/ui/AdminTableShell.vue'
import StatusTag from '@/components/ui/StatusTag.vue'
import ConfirmActionDialog from '@/components/ui/ConfirmActionDialog.vue'

const localVue = createLocalVue()
localVue.use(ElementUI)

describe('admin console UI components', () => {
  it('renders a compact admin heading with context and actions', () => {
    const wrapper = shallowMount(AdminPageHeader, {
      localVue,
      propsData: {
        title: '订单管理',
        description: '跟进履约状态与异常订单。',
        eyebrow: '交易中心'
      },
      slots: { actions: '<button data-testid="create-order">新建订单</button>' }
    })

    expect(wrapper.get('h1').text()).toBe('订单管理')
    expect(wrapper.text()).toContain('交易中心')
    expect(wrapper.find('[data-testid="create-order"]').exists()).toBe(true)
  })

  it('keeps filters, table content and pagination in one semantic shell', () => {
    const wrapper = shallowMount(AdminTableShell, {
      localVue,
      propsData: { title: '订单列表', total: 12 },
      slots: {
        filters: '<input data-testid="order-filter">',
        default: '<table data-testid="orders-table"></table>',
        pagination: '<button data-testid="next-page">下一页</button>'
      }
    })

    expect(wrapper.get('[data-testid="admin-table-shell"]').attributes('aria-label')).toBe('订单列表')
    expect(wrapper.text()).toContain('共 12 条')
    expect(wrapper.find('[data-testid="order-filter"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="orders-table"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="next-page"]').exists()).toBe(true)
  })

  it.each([
    ['COMPLETED', 'success'],
    ['PENDING', 'warning'],
    ['REJECTED', 'danger'],
    ['CANCELLED', 'muted'],
    ['IN_PROGRESS', 'info'],
    ['AI_REVIEW', 'ai']
  ])('maps %s to the shared %s status tone', (status, tone) => {
    const wrapper = shallowMount(StatusTag, {
      localVue,
      propsData: { status, label: '状态' }
    })

    expect(wrapper.classes()).toContain(`ps-status-tag--${tone}`)
    expect(wrapper.attributes('data-status')).toBe(status)
  })

  it('collects a required reason before confirming a risky action', async () => {
    const wrapper = shallowMount(ConfirmActionDialog, {
      localVue,
      propsData: {
        visible: true,
        title: '取消订单',
        description: '取消后不可恢复。',
        requireReason: true
      }
    })

    wrapper.setData({ reason: '  用户申请取消  ' })
    await wrapper.vm.$nextTick()
    wrapper.vm.confirm()

    expect(wrapper.emitted('confirm')[0]).toEqual(['用户申请取消'])
    expect(wrapper.emitted('update:visible')[0]).toEqual([false])
  })
})
