import { shallowMount } from '@vue/test-utils'
import HomeView from '@/views/HomeView.vue'
import { listActiveBanners } from '@/api/banner'

jest.mock('@/api/banner', () => ({
  listActiveBanners: jest.fn()
}))

describe('HomeView', () => {
  beforeEach(() => {
    jest.clearAllMocks()
  })

  it('loads and renders active banners', async () => {
    listActiveBanners.mockResolvedValue({
      data: [{ id: 'b-1', title: '夏日服务季', subtitle: '一站式守护', imageUrl: 'https://example.com/banner.png', targetUrl: '/services' }]
    })
    const push = jest.fn()
    const wrapper = shallowMount(HomeView, {
      mocks: { $router: { push } },
      stubs: ['el-carousel', 'el-carousel-item', 'el-alert', 'router-link']
    })
    await flush()

    expect(listActiveBanners).toHaveBeenCalledWith({ limit: 5 })
    expect(wrapper.vm.banners[0].title).toBe('夏日服务季')
    expect(wrapper.findComponent({ name: 'PageHero' }).exists()).toBe(true)
    expect(wrapper.findAllComponents({ name: 'FeatureCard' }).length).toBeGreaterThanOrEqual(6)

    const event = { preventDefault: jest.fn() }
    wrapper.vm.handleBannerClick(event, wrapper.vm.banners[0])
    expect(event.preventDefault).toHaveBeenCalled()
    expect(push).toHaveBeenCalledWith('/services')
  })

  it('stores a non-blocking error when banners fail', async () => {
    listActiveBanners.mockRejectedValue(new Error('网络异常'))
    const wrapper = shallowMount(HomeView, {
      mocks: { $router: { push: jest.fn() } },
      stubs: ['el-carousel', 'el-carousel-item', 'el-alert', 'router-link']
    })
    await flush()

    expect(wrapper.vm.error).toBe('网络异常')
    expect(wrapper.findComponent({ name: 'ErrorState' }).exists()).toBe(true)
    expect(wrapper.findComponent({ name: 'ErrorState' }).props('description')).toBe('网络异常')
  })
})

function flush() {
  return new Promise(resolve => setTimeout(resolve, 0))
}
