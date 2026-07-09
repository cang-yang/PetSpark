import { shallowMount } from '@vue/test-utils'
import MyCommunityPostsView from '@/views/MyCommunityPostsView.vue'
import AdminCommunityView from '@/views/AdminCommunityView.vue'
import { listAdminCommunityPosts, listMyCommunityPosts, moderateCommunityPost } from '@/api/community'

jest.mock('@/api/community', () => ({
  listAdminCommunityPosts: jest.fn(),
  listMyCommunityPosts: jest.fn(),
  moderateCommunityPost: jest.fn()
}))

const stubs = {
  'el-card': { template: '<div><slot /></div>' },
  'el-select': { template: '<select><slot /></select>' },
  'el-option': true,
  'el-input': true,
  'el-button': { template: '<button @click="$emit(\'click\')"><slot /></button>' },
  'el-table': { props: ['data'], template: '<table><slot /></table>' },
  'el-table-column': true,
  'router-link': { template: '<a><slot /></a>' }
}

describe('community management views', () => {
  beforeEach(() => {
    jest.clearAllMocks()
    listMyCommunityPosts.mockResolvedValue({ data: { items: [{ id: 'p1', title: '我的帖子', status: 'PUBLISHED' }] } })
    listAdminCommunityPosts.mockResolvedValue({ data: { items: [{ id: 'p2', title: '待处理', status: 'PUBLISHED', version: 3 }] } })
    moderateCommunityPost.mockResolvedValue({ data: { id: 'p2', status: 'HIDDEN' } })
  })

  it('loads my community posts', async () => {
    const wrapper = shallowMount(MyCommunityPostsView, { stubs })
    await Promise.resolve()
    await wrapper.vm.$nextTick()
    expect(listMyCommunityPosts).toHaveBeenCalledWith({ status: undefined, page: 1, size: 50 })
    expect(wrapper.vm.posts[0].title).toBe('我的帖子')
  })

  it('loads admin posts and moderates status', async () => {
    const wrapper = shallowMount(AdminCommunityView, { stubs, mocks: { $message: { success: jest.fn(), error: jest.fn() } } })
    await Promise.resolve()
    await wrapper.vm.$nextTick()
    expect(listAdminCommunityPosts).toHaveBeenCalledWith({ keyword: undefined, status: undefined, page: 1, size: 50 })

    await wrapper.vm.moderate({ id: 'p2', version: 3 }, 'HIDDEN')
    expect(moderateCommunityPost).toHaveBeenCalledWith('p2', { status: 'HIDDEN', reason: '社区规范处理', version: 3 })
  })
})
