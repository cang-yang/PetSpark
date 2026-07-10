import { shallowMount } from '@vue/test-utils'
import AdminDashboardView from '@/views/AdminDashboardView.vue'
import { getDashboardSummary } from '@/api/dashboard'
import * as echarts from 'echarts'

jest.mock('@/api/dashboard', () => ({
  getDashboardSummary: jest.fn()
}))

jest.mock('echarts', () => ({
  init: jest.fn(() => ({
    setOption: jest.fn(),
    dispose: jest.fn()
  }))
}))

describe('AdminDashboardView', () => {
  beforeEach(() => {
    jest.clearAllMocks()
    getDashboardSummary.mockResolvedValue({
      data: {
        metrics: [
          { key: 'users', label: '注册用户', value: 5 },
          { key: 'pets', label: '宠物', value: 8 },
          { key: 'orders', label: '订单', value: 3 }
        ],
        distributions: [
          { key: 'orders', label: '订单状态分布', items: [{ status: 'CREATED', count: 1 }, { status: 'COMPLETED', count: 2 }] },
          { key: 'outbox', label: 'Outbox 事件状态分布', items: [{ status: 'SENT', count: 10 }] }
        ],
        queryCount: 17
      }
    })
  })

  it('loads summary and renders metric cards', async () => {
    const wrapper = shallowMount(AdminDashboardView, {
      mocks: { $message: { success: jest.fn(), error: jest.fn() } }
    })
    await flush()

    expect(getDashboardSummary).toHaveBeenCalled()

    const metrics = wrapper.findAll('[data-testid^="metric-"]')
    expect(metrics.length).toBe(3)
    const usersCard = wrapper.findAllComponents({ name: 'MetricCard' }).at(0)
    expect(usersCard.props('value')).toBe(5)
    expect(usersCard.props('label')).toBe('注册用户')
  })

  it('renders chart containers for each distribution', async () => {
    const wrapper = shallowMount(AdminDashboardView, {
      mocks: { $message: { success: jest.fn(), error: jest.fn() } }
    })
    await flush()

    const charts = wrapper.findAll('[data-testid^="chart-"]')
    expect(charts.length).toBe(2)
    expect(wrapper.find('[data-testid="chart-orders"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="chart-outbox"]').exists()).toBe(true)
  })

  it('initializes charts after loading reveals their containers', async () => {
    shallowMount(AdminDashboardView, {
      mocks: { $message: { success: jest.fn(), error: jest.fn() } }
    })
    await flush()
    await flush()

    expect(echarts.init).toHaveBeenCalledTimes(2)
  })

  it('shows error message on API failure', async () => {
    getDashboardSummary.mockRejectedValue(new Error('网络错误'))
    const wrapper = shallowMount(AdminDashboardView, {
      mocks: { $message: { success: jest.fn(), error: jest.fn() } }
    })
    await flush()

    expect(wrapper.find('[data-testid="dashboard-error"]').exists()).toBe(true)
    expect(wrapper.findComponent({ name: 'ErrorState' }).props('description')).toContain('网络错误')
  })

  it('shows loading indicator before data arrives', async () => {
    getDashboardSummary.mockReturnValue(new Promise(() => {}))
    const wrapper = shallowMount(AdminDashboardView, {
      mocks: { $message: { success: jest.fn(), error: jest.fn() } }
    })

    expect(wrapper.find('[data-testid="dashboard-loading"]').exists()).toBe(true)
    expect(wrapper.findComponent({ name: 'LoadingState' }).exists()).toBe(true)
  })
})

function flush() {
  return new Promise(resolve => setTimeout(resolve, 0))
}
