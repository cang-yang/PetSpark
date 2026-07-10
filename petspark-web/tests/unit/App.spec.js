import { createLocalVue, shallowMount } from '@vue/test-utils'
import VueRouter from 'vue-router'
import App from '@/App.vue'
import PublicLayout from '@/layouts/PublicLayout.vue'
import AdminLayout from '@/layouts/AdminLayout.vue'
import AuthLayout from '@/layouts/AuthLayout.vue'

jest.mock('@/api/auth', () => ({ logout: jest.fn() }))
jest.mock('@/api/notifications', () => ({ getUnreadNotificationCount: jest.fn() }))

describe('App', () => {
  it('renders the PetSpark application shell', () => {
    jest.useFakeTimers()
    const localVue = createLocalVue()
    localVue.use(VueRouter)
    const wrapper = shallowMount(App, {
      localVue,
      router: new VueRouter(),
      mocks: {
        $store: {
          getters: { isAuthenticated: false },
          state: { user: null, notificationUnreadCount: 0 },
          dispatch: jest.fn()
        }
      }
    })

    expect(wrapper.get('[data-testid="app-title"]').text()).toBe('PetSpark')
    expect(wrapper.find('router-view-stub').exists()).toBe(true)
    jest.useRealTimers()
  })

  it('shows notification unread badge for authenticated users', () => {
    jest.useFakeTimers()
    const localVue = createLocalVue()
    localVue.use(VueRouter)
    const dispatch = jest.fn().mockResolvedValue(7)
    const wrapper = shallowMount(App, {
      localVue,
      router: new VueRouter(),
      mocks: {
        $store: {
          getters: { isAuthenticated: true },
          state: { user: { nickname: 'tester' }, notificationUnreadCount: 7 },
          dispatch
        }
      }
    })

    expect(wrapper.findComponent(PublicLayout).props('notificationUnreadCount')).toBe(7)
    expect(wrapper.findComponent(PublicLayout).props('notificationUnreadCountText')).toBe('7')
    expect(dispatch).toHaveBeenCalledWith('refreshNotificationUnreadCount')
    jest.useRealTimers()
  })

  it.each([
    ['/login', AuthLayout],
    ['/admin/users', AdminLayout],
    ['/pets', PublicLayout]
  ])('uses the route-aware shell for %s', async (path, expectedLayout) => {
    jest.useFakeTimers()
    const localVue = createLocalVue()
    localVue.use(VueRouter)
    const router = new VueRouter({
      routes: [
        { path: '/login', meta: { layout: 'auth' } },
        { path: '/admin/users', meta: { layout: 'admin' } },
        { path: '/pets', meta: { layout: 'public' } }
      ]
    })
    await router.push(path)
    const wrapper = shallowMount(App, {
      localVue,
      router,
      mocks: {
        $store: {
          getters: { isAuthenticated: false },
          state: { user: null, notificationUnreadCount: 0 },
          dispatch: jest.fn()
        }
      }
    })

    expect(wrapper.findComponent(expectedLayout).exists()).toBe(true)
    if (expectedLayout === AuthLayout) {
      expect(wrapper.findComponent(AuthLayout).attributes('publicnav')).toBeUndefined()
      expect(wrapper.findComponent(AuthLayout).attributes('adminnav')).toBeUndefined()
    }
    wrapper.destroy()
    jest.useRealTimers()
  })

  it.each([
    ['/pets', 'companion'],
    ['/boarding/new', 'service'],
    ['/ai/chat', 'ai']
  ])('passes the %s route atmosphere to the public shell', async (path, scene) => {
    jest.useFakeTimers()
    const localVue = createLocalVue()
    localVue.use(VueRouter)
    const router = new VueRouter({ routes: [{ path, meta: { layout: 'public' } }] })
    await router.push(path)
    const wrapper = shallowMount(App, {
      localVue,
      router,
      mocks: {
        $store: {
          getters: { isAuthenticated: false },
          state: { user: null, notificationUnreadCount: 0 },
          dispatch: jest.fn()
        }
      }
    })

    expect(wrapper.findComponent(PublicLayout).props('scene')).toBe(scene)
    wrapper.destroy()
    jest.useRealTimers()
  })
})
