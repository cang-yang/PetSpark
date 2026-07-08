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
import { listNotifications } from '@/api/notifications'

function mountView(query = {}) {
  const localVue = createLocalVue()
  localVue.use(VueRouter)
  localVue.use(Vuex)
  localVue.use(ElementUI)
  const store = new Vuex.Store({
    state: { accessToken: 't', user: { nickname: 'tester' } },
    getters: { isAuthenticated: () => true }
  })
  const testRouter = new VueRouter({
    mode: 'abstract',
    routes: [{ path: '/notifications', name: 'notifications', component: NotificationsView }]
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
  beforeEach(() => listNotifications.mockReset())

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
})

// 刷新微任务队列直到 resolved/rejected 的 mock 落定并触发组件重渲染。
function flushPromises() {
  return new Promise((resolve) => setTimeout(resolve, 0))
}
