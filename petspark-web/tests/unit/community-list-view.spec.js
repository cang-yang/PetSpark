import { shallowMount } from '@vue/test-utils'
import CommunityListView from '@/views/CommunityListView.vue'
import { createCommunityPost, listCommunityPosts } from '@/api/community'

const flushPromises = () => new Promise(resolve => setTimeout(resolve, 0))

jest.mock('@/api/community', () => ({
  listCommunityPosts: jest.fn(),
  createCommunityPost: jest.fn()
}))

const stubs = {
  'el-card': { template: '<div><slot /></div>' },
  'el-input': { props: ['value'], template: '<input :value="value" @input="$emit(\'input\', $event.target.value)" />' },
  'el-button': { template: '<button @click="$emit(\'click\')"><slot /></button>' },
  'el-form': { template: '<form><slot /></form>' },
  'el-form-item': { template: '<div><slot /></div>' },
  'el-pagination': true,
  'router-link': { props: ['to'], template: '<a><slot /></a>' }
}

describe('CommunityListView', () => {
  beforeEach(() => {
    jest.clearAllMocks()
    listCommunityPosts.mockResolvedValue({ data: { items: [{ id: 'p1', title: '晒猫', content: '内容', authorName: '小花', likeCount: 1, favoriteCount: 2, commentCount: 3 }], total: 1 } })
    createCommunityPost.mockResolvedValue({ data: { id: 'p2' } })
  })

  it('loads community posts on create', async () => {
    const wrapper = shallowMount(CommunityListView, { stubs })
    await flushPromises()
    await wrapper.vm.$nextTick()

    expect(listCommunityPosts).toHaveBeenCalledWith({ keyword: undefined, page: 1, size: 10 })
    expect(wrapper.text()).toContain('晒猫')
    expect(wrapper.text()).toContain('赞 1')
  })

  it('creates a post and navigates to detail', async () => {
    const push = jest.fn()
    const wrapper = shallowMount(CommunityListView, { stubs, mocks: { $router: { push }, $message: { success: jest.fn(), warning: jest.fn(), error: jest.fn() } } })
    await Promise.resolve()
    wrapper.setData({ form: { title: '新帖', content: '正文' } })
    await wrapper.vm.submitPost()

    expect(createCommunityPost).toHaveBeenCalledWith({ title: '新帖', content: '正文' })
    expect(push).toHaveBeenCalledWith('/community/posts/p2')
  })
})
