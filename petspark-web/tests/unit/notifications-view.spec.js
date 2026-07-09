jest.mock('@/api/http', () => ({ get: jest.fn(), post: jest.fn(), put: jest.fn() }))
jest.mock('@/api/notifications', () => ({
  listNotifications: jest.fn(),
  markNotificationRead: jest.fn(),
  markAllNotificationsRead: jest.fn()
}))

import { createLocalVue, shallowMount } from '@vue/test-utils'
import VueRouter from 'vue-router'
import Vuex from 'vuex'
import ElementUI from 'element-ui'
import NotificationsView from '@/views/NotificationsView.vue'
import { listNotifications, markNotificationRead, markAllNotificationsRead } from '@/api/notifications'

function mountView(query = {}) {
  const localVue = createLocalVue()
  localVue.use(VueRouter)
  localVue.use(Vuex)
  localVue.use(ElementUI)
  const store = new Vuex.Store({
    state: { accessToken: 't', user: { nickname: 'tester' }, notificationUnreadCount: 0 },
    getters: { isAuthenticated: () => true },
    actions: {
      setNotificationUnreadCount({ state }, count) {
        state.notificationUnreadCount = count
      }
    }
  })
  const testRouter = new VueRouter({
    mode: 'abstract',
    routes: [
      { path: '/notifications', name: 'notifications', component: NotificationsView },
      { path: '/my/adoptions', name: 'my-adoptions' },
      { path: '/my/boarding', name: 'my-boarding' },
      { path: '/my/services/bookings', name: 'my-service-bookings' },
      { path: '/my/orders', name: 'my-orders' }
    ]
  })
  testRouter.push({ path: '/notifications', query })
  return shallowMount(NotificationsView, {
    localVue,
    router: testRouter,
    store,
    mocks: { $message: { success: jest.fn(), error: jest.fn() } }
  })
}

describe('NotificationsView', () => {
  beforeEach(() => {
    listNotifications.mockReset()
    markNotificationRead.mockReset()
    markAllNotificationsRead.mockReset()
  })

  it('lists notifications on mount and renders items', async () => {
    listNotifications.mockResolvedValue({
      data: {
        items: [
          { id: 'n-1', type: 'SYSTEM', title: '标题1', content: '内容1', read: false, readAt: null, createdAt: '2026-07-08T09:00:00Z' },
          { id: 'n-2', type: 'ADOPTION', title: '标题2', content: '内容2', read: true, readAt: '2026-07-08T08:00:00Z', createdAt: '2026-07-08T07:00:00Z' }
        ],
        page: 1, size: 10, total: 2, unreadCount: 1
      }
    })

    const wrapper = mountView()
    await flushPromises()

    expect(listNotifications).toHaveBeenCalledWith({ page: 1, size: 10, onlyUnread: false })
    expect(wrapper.find('[data-testid="notification-list"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="notification-n-1"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="unread-dot"]').exists()).toBe(true)
  })

  it('shows empty StatusPanel when no notifications', async () => {
    listNotifications.mockResolvedValue({
      data: { items: [], page: 1, size: 10, total: 0, unreadCount: 0 }
    })

    const wrapper = mountView()
    await flushPromises()

    // 空状态用 StatusPanel 表达；shallow 下 StatusPanel 被桩替，按 props 断言状态语义。
    const panel = wrapper.findComponent({ name: 'StatusPanel' })
    expect(panel.exists()).toBe(true)
    expect(panel.props('status')).toBe('empty')
    expect(panel.props('testId')).toBe('empty-panel')
    expect(wrapper.find('[data-testid="notification-list"]').exists()).toBe(false)
  })

  it('shows error StatusPanel with retry when load fails', async () => {
    listNotifications.mockRejectedValue(new Error('请求失败'))

    const wrapper = mountView()
    await flushPromises()

    const panel = wrapper.findComponent({ name: 'StatusPanel' })
    expect(panel.exists()).toBe(true)
    expect(panel.props('status')).toBe('error')
    expect(panel.props('reason')).toContain('请求失败')
  })

  it('syncs page and onlyUnread from URL query', async () => {
    listNotifications.mockResolvedValue({
      data: { items: [], page: 3, size: 10, total: 0, unreadCount: 0 }
    })

    mountView({ page: '3', onlyUnread: 'true' })

    await flushPromises()

    expect(listNotifications).toHaveBeenCalledWith({ page: 3, size: 10, onlyUnread: true })
  })

  it('removes read item from only-unread list and updates shared unread count', async () => {
    listNotifications
      .mockResolvedValueOnce({
        data: {
          items: [
            { id: 'n-1', type: 'BOARDING_CONFIRMED', title: '寄养确认', content: '内容', businessType: 'BOARDING', businessId: 'b-1', read: false, readAt: null, createdAt: '2026-07-08T09:00:00Z' }
          ],
          page: 1, size: 10, total: 1, unreadCount: 1
        }
      })
      .mockResolvedValueOnce({
        data: { items: [], page: 1, size: 10, total: 0, unreadCount: 0 }
      })
    markNotificationRead.mockResolvedValue({ data: null })

    const wrapper = mountView({ onlyUnread: 'true' })
    await flushPromises()

    await wrapper.vm.markOne(wrapper.vm.items[0])
    await flushPromises()

    expect(markNotificationRead).toHaveBeenCalledWith('n-1')
    expect(wrapper.find('[data-testid="notification-n-1"]').exists()).toBe(false)
    expect(wrapper.vm.$store.state.notificationUnreadCount).toBe(0)
  })

  it('marks all unread notifications read and clears only-unread list', async () => {
    listNotifications.mockResolvedValue({
      data: {
        items: [
          { id: 'n-1', type: 'SYSTEM', title: '标题', content: '内容', read: false, readAt: null, createdAt: '2026-07-08T09:00:00Z' }
        ],
        page: 1, size: 10, total: 1, unreadCount: 1
      }
    })
    markAllNotificationsRead.mockResolvedValue({ data: null })

    const wrapper = mountView({ onlyUnread: 'true' })
    await flushPromises()

    await wrapper.vm.markAll()
    await flushPromises()

    expect(markAllNotificationsRead).toHaveBeenCalled()
    expect(wrapper.find('[data-testid="notification-list"]').exists()).toBe(false)
    expect(wrapper.vm.$store.state.notificationUnreadCount).toBe(0)
  })

  it('opens business target and marks unread notification read first', async () => {
    listNotifications.mockResolvedValue({
      data: {
        items: [
          { id: 'n-1', type: 'ADOPTION_APPROVED', title: '通过', content: '内容', businessType: 'ADOPTION', businessId: 'app-1', read: false, readAt: null, createdAt: '2026-07-08T09:00:00Z' }
        ],
        page: 1, size: 10, total: 1, unreadCount: 1
      }
    })
    markNotificationRead.mockResolvedValue({ data: null })

    const wrapper = mountView()
    await flushPromises()

    await wrapper.vm.openTarget(wrapper.vm.items[0])
    await flushPromises()

    expect(markNotificationRead).toHaveBeenCalledWith('n-1')
    expect(wrapper.vm.$route.name).toBe('my-adoptions')
    expect(wrapper.vm.$route.query.highlight).toBe('app-1')
  })
})

// 刷新微任务队列直到 resolved/rejected 的 mock 落定并触发组件重渲染。
function flushPromises() {
  return new Promise((resolve) => setTimeout(resolve, 0))
}
