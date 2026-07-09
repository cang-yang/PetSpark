import { shallowMount } from '@vue/test-utils'
import CommunityDetailView from '@/views/CommunityDetailView.vue'
import { createCommunityComment, getCommunityPost, likeCommunityPost, listCommunityComments, unlikeCommunityPost } from '@/api/community'

const flushPromises = () => new Promise(resolve => setTimeout(resolve, 0))

jest.mock('@/api/community', () => ({
  createCommunityComment: jest.fn(),
  favoriteCommunityPost: jest.fn(),
  getCommunityPost: jest.fn(),
  likeCommunityPost: jest.fn(),
  listCommunityComments: jest.fn(),
  unfavoriteCommunityPost: jest.fn(),
  unlikeCommunityPost: jest.fn()
}))

const stubs = {
  'el-card': { template: '<div><slot /></div>' },
  'el-input': { props: ['value'], template: '<textarea :value="value" @input="$emit(\'input\', $event.target.value)" />' },
  'el-button': { template: '<button @click="$emit(\'click\')"><slot /></button>' },
  'el-form': { template: '<form><slot /></form>' }
}

describe('CommunityDetailView', () => {
  beforeEach(() => {
    jest.clearAllMocks()
    getCommunityPost.mockResolvedValue({ data: { id: 'p1', title: '晒猫', content: '正文', authorName: '小花', liked: false, favorited: false, likeCount: 0, favoriteCount: 0 } })
    listCommunityComments.mockResolvedValue({ data: { items: [{ id: 'c1', authorName: '阿黄', content: '真可爱' }], total: 1 } })
    likeCommunityPost.mockResolvedValue({ data: { id: 'p1', title: '晒猫', content: '正文', liked: true, likeCount: 1 } })
    unlikeCommunityPost.mockResolvedValue({ data: { id: 'p1', title: '晒猫', content: '正文', liked: false, likeCount: 0 } })
    createCommunityComment.mockResolvedValue({ data: { id: 'c2' } })
  })

  it('loads detail and comments', async () => {
    const wrapper = shallowMount(CommunityDetailView, { stubs, mocks: { $route: { params: { id: 'p1' } } } })
    await flushPromises()
    await wrapper.vm.$nextTick()

    expect(getCommunityPost).toHaveBeenCalledWith('p1')
    expect(listCommunityComments).toHaveBeenCalledWith('p1', { page: 1, size: 100 })
    expect(wrapper.text()).toContain('晒猫')
    expect(wrapper.text()).toContain('真可爱')
  })

  it('toggles like and submits comment', async () => {
    const wrapper = shallowMount(CommunityDetailView, { stubs, mocks: { $route: { params: { id: 'p1' } }, $message: { success: jest.fn(), warning: jest.fn(), error: jest.fn() } } })
    await flushPromises()
    await wrapper.vm.$nextTick()

    await wrapper.vm.toggleLike()
    expect(likeCommunityPost).toHaveBeenCalledWith('p1')

    wrapper.setData({ commentForm: { content: '好看' } })
    await wrapper.vm.submitComment()
    expect(createCommunityComment).toHaveBeenCalledWith('p1', { content: '好看' })
  })
})
