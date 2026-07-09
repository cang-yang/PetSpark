import http from '@/api/http'
import {
  createCommunityComment,
  createCommunityPost,
  favoriteCommunityPost,
  getCommunityPost,
  likeCommunityPost,
  listAdminCommunityPosts,
  listCommunityComments,
  listCommunityPosts,
  listMyCommunityPosts,
  moderateCommunityComment,
  moderateCommunityPost,
  unfavoriteCommunityPost,
  unlikeCommunityPost
} from '@/api/community'

jest.mock('@/api/http', () => ({
  get: jest.fn(),
  post: jest.fn()
}))

describe('community API', () => {
  beforeEach(() => {
    Object.values(http).forEach(fn => fn.mockReset())
  })

  it('calls post and comment endpoints', () => {
    listCommunityPosts({ keyword: '猫', page: 1, size: 10 })
    expect(http.get).toHaveBeenCalledWith('/api/v1/community/posts', { params: { keyword: '猫', page: 1, size: 10 } })

    createCommunityPost({ title: '晒猫', content: '今天很好' })
    expect(http.post).toHaveBeenCalledWith('/api/v1/community/posts', { title: '晒猫', content: '今天很好' })

    getCommunityPost('post-1')
    expect(http.get).toHaveBeenCalledWith('/api/v1/community/posts/post-1')

    listCommunityComments('post-1', { page: 1, size: 20 })
    expect(http.get).toHaveBeenCalledWith('/api/v1/community/posts/post-1/comments', { params: { page: 1, size: 20 } })

    createCommunityComment('post-1', { content: '赞' })
    expect(http.post).toHaveBeenCalledWith('/api/v1/community/posts/post-1/comments', { content: '赞' })
  })

  it('calls interaction and moderation endpoints', () => {
    likeCommunityPost('post-1')
    unlikeCommunityPost('post-1')
    favoriteCommunityPost('post-1')
    unfavoriteCommunityPost('post-1')
    expect(http.post).toHaveBeenCalledWith('/api/v1/community/posts/post-1/like')
    expect(http.post).toHaveBeenCalledWith('/api/v1/community/posts/post-1/unlike')
    expect(http.post).toHaveBeenCalledWith('/api/v1/community/posts/post-1/favorite')
    expect(http.post).toHaveBeenCalledWith('/api/v1/community/posts/post-1/unfavorite')

    listMyCommunityPosts({ status: 'PUBLISHED' })
    expect(http.get).toHaveBeenCalledWith('/api/v1/community/my/posts', { params: { status: 'PUBLISHED' } })

    listAdminCommunityPosts({ status: 'HIDDEN' })
    expect(http.get).toHaveBeenCalledWith('/api/v1/admin/community/posts', { params: { status: 'HIDDEN' } })

    moderateCommunityPost('post-1', { status: 'HIDDEN', reason: '违规', version: 1 })
    expect(http.post).toHaveBeenCalledWith('/api/v1/admin/community/posts/post-1/moderation', { status: 'HIDDEN', reason: '违规', version: 1 })

    moderateCommunityComment('comment-1', { status: 'PUBLISHED', reason: '恢复', version: 2 })
    expect(http.post).toHaveBeenCalledWith('/api/v1/admin/community/comments/comment-1/moderation', { status: 'PUBLISHED', reason: '恢复', version: 2 })
  })
})
