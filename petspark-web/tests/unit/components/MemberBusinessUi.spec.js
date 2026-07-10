jest.mock('@/assets/placeholders/pet-cat.png', () => 'pet-cat.png')
jest.mock(
  '@/assets/placeholders/service-medical.png',
  () => 'service-medical.png'
)

import { createLocalVue, mount, shallowMount } from '@vue/test-utils'
import ElementUI from 'element-ui'
import PetAvatar from '@/components/pet/PetAvatar.vue'
import PetCard from '@/components/pet/PetCard.vue'
import ServiceCard from '@/components/service/ServiceCard.vue'
import OrderStatusCard from '@/components/order/OrderStatusCard.vue'
import StatusTimeline from '@/components/ui/StatusTimeline.vue'

const localVue = createLocalVue()
localVue.use(ElementUI)

describe('member business components', () => {
  it('uses a species placeholder when a pet has no image', () => {
    const wrapper = mount(PetAvatar, {
      localVue,
      propsData: { pet: { name: '团子', species: 'CAT' } },
    })

    expect(wrapper.get('img').attributes('alt')).toBe('团子的头像')
    expect(wrapper.get('img').attributes('src')).toContain('pet-cat')
  })

  it('renders the pet identity, status and next action', async () => {
    const wrapper = shallowMount(PetCard, {
      localVue,
      propsData: {
        pet: {
          id: 'pet-1',
          name: '团子',
          species: 'CAT',
          breedName: '中华田园猫',
        },
        status: '等待回家',
        actionText: '申请领养',
      },
      stubs: ['router-link', 'PetAvatar'],
    })

    expect(wrapper.text()).toContain('团子')
    expect(wrapper.text()).toContain('中华田园猫')
    expect(wrapper.text()).toContain('等待回家')
    wrapper.findComponent({ name: 'ElButton' }).vm.$emit('click')
    await wrapper.vm.$nextTick()
    expect(wrapper.emitted('action')).toEqual([
      [expect.objectContaining({ id: 'pet-1' })],
    ])
  })

  it('renders service artwork, price and safety notice', () => {
    const wrapper = shallowMount(ServiceCard, {
      localVue,
      propsData: {
        service: {
          id: 'medical-1',
          name: '基础体检',
          kind: 'MEDICAL',
          basePrice: 128,
        },
        notice: '线下预约，不提供在线诊断或处方',
      },
      stubs: ['router-link', 'el-button'],
    })

    expect(wrapper.text()).toContain('基础体检')
    expect(wrapper.text()).toContain('￥128')
    expect(wrapper.text()).toContain('线下预约，不提供在线诊断或处方')
    expect(wrapper.get('img').attributes('src')).toContain('service-medical')
  })

  it('makes order state and next step prominent', () => {
    const wrapper = shallowMount(OrderStatusCard, {
      localVue,
      propsData: {
        order: {
          id: 'order-1',
          orderNo: 'ORD-1',
          status: 'PROCESSING',
          totalAmount: 68,
        },
        statusLabel: '处理中',
        nextStep: '等待商家备货',
      },
      stubs: ['el-button'],
    })

    expect(wrapper.text()).toContain('ORD-1')
    expect(wrapper.text()).toContain('处理中')
    expect(wrapper.text()).toContain('￥68')
    expect(wrapper.text()).toContain('等待商家备货')
  })

  it('marks completed and current timeline steps', () => {
    const wrapper = mount(StatusTimeline, {
      propsData: {
        items: [
          { title: '提交申请', description: '已收到申请' },
          { title: '平台审核', description: '正在核验信息' },
          { title: '完成交接', description: '等待后续安排' },
        ],
        active: 1,
      },
    })

    expect(wrapper.findAll('[data-testid="timeline-step"]')).toHaveLength(3)
    expect(wrapper.findAll('[data-state="done"]')).toHaveLength(1)
    expect(wrapper.findAll('[data-state="current"]')).toHaveLength(1)
  })
})
