import { shallowMount } from '@vue/test-utils'
import HomeView from '@/views/HomeView.vue'
import { listActiveBanners } from '@/api/banner'

jest.mock('@/api/banner', () => ({
  listActiveBanners: jest.fn()
}))

function mountHome({ push = jest.fn(), authenticated = false } = {}) {
  return shallowMount(HomeView, {
    mocks: {
      $router: { push },
      $store: { getters: { isAuthenticated: authenticated } }
    },
    stubs: ['el-carousel', 'el-carousel-item', 'el-alert', 'router-link']
  })
}

describe('HomeView', () => {
  beforeEach(() => {
    jest.clearAllMocks()
  })

  it('loads and renders active banners', async () => {
    listActiveBanners.mockResolvedValue({
      data: [{ id: 'b-1', title: '夏日服务季', subtitle: '一站式守护', imageUrl: 'https://example.com/banner.png', targetUrl: '/services' }]
    })
    const push = jest.fn()
    const wrapper = mountHome({ push, authenticated: true })
    await flush()

    expect(listActiveBanners).toHaveBeenCalledWith({ limit: 5 })
    expect(wrapper.vm.banners[0].title).toBe('夏日服务季')
    expect(wrapper.vm.displayBanners).toHaveLength(3)
    expect(wrapper.findComponent({ name: 'PageHero' }).exists()).toBe(true)
    expect(wrapper.findAllComponents({ name: 'FeatureCard' }).length).toBeGreaterThanOrEqual(6)

    const event = { preventDefault: jest.fn() }
    wrapper.vm.handleBannerClick(event, wrapper.vm.banners[0])
    expect(event.preventDefault).toHaveBeenCalled()
    expect(push).toHaveBeenCalledWith('/services')
  })

  it('keeps a complete anonymous homepage when the banner API fails', async () => {
    listActiveBanners.mockRejectedValue(new Error('网络异常'))
    const wrapper = mountHome()
    await flush()

    expect(wrapper.vm.bannerLoadFailed).toBe(true)
    expect(wrapper.vm.displayBanners).toHaveLength(3)
    expect(wrapper.find('[data-testid="home-banner-carousel"]').exists()).toBe(true)
    expect(wrapper.text()).not.toContain('推荐内容暂时未加载')
  })

  it('configures visible controls and automatic rotation', () => {
    listActiveBanners.mockImplementation(() => new Promise(() => {}))
    const wrapper = mountHome()

    const carousel = wrapper.find('[data-testid="home-banner-carousel"]')
    expect(carousel.attributes('interval')).toBe('5200')
    expect(carousel.attributes('autoplay')).toBe('true')
    expect(carousel.attributes('arrow')).toBe('always')
    expect(carousel.attributes('pause-on-hover')).toBe('true')
    wrapper.find('[data-testid="home-banner-motion"]').trigger('click')
    return wrapper.vm.$nextTick().then(() => {
      expect(wrapper.vm.carouselAutoplay).toBe(false)
      expect(wrapper.find('[data-testid="home-banner-motion"]').text()).toBe('继续轮播')
    })
  })

  it('sends anonymous users to login when a banner action needs an account', () => {
    listActiveBanners.mockImplementation(() => new Promise(() => {}))
    const push = jest.fn()
    const wrapper = mountHome({ push })
    const event = { preventDefault: jest.fn() }

    wrapper.vm.handleBannerClick(event, wrapper.vm.displayBanners[0])

    expect(push).toHaveBeenCalledWith({ path: '/login', query: { redirect: '/boarding/new' } })
  })

  it('starts paused for reduced motion and allows an explicit opt in', async () => {
    listActiveBanners.mockImplementation(() => new Promise(() => {}))
    const wrapper = mountHome()
    await wrapper.setData({ prefersReducedMotion: true, motionOptIn: false })

    expect(wrapper.vm.carouselAutoplay).toBe(false)
    expect(wrapper.find('[data-testid="home-banner-motion"]').text()).toBe('继续轮播')

    await wrapper.find('[data-testid="home-banner-motion"]').trigger('click')
    expect(wrapper.vm.motionOptIn).toBe(true)
    expect(wrapper.vm.carouselAutoplay).toBe(true)
  })
})

function flush() {
  return new Promise(resolve => setTimeout(resolve, 0))
}
