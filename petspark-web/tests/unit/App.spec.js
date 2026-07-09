import { createLocalVue, shallowMount } from '@vue/test-utils'
import VueRouter from 'vue-router'
import App from '@/App.vue'

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

    expect(wrapper.get('[data-testid="nav-notifications-badge"]').text()).toBe('7')
    expect(dispatch).toHaveBeenCalledWith('refreshNotificationUnreadCount')
    jest.useRealTimers()
  })
})
