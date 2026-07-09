import http from '@/api/http'

/** Community API: posts, comments, like/favorite interactions, and moderation. */
export function listCommunityPosts(params) {
  return http.get('/api/v1/community/posts', { params })
}

export function createCommunityPost(payload) {
  return http.post('/api/v1/community/posts', payload)
}

export function getCommunityPost(id) {
  return http.get(`/api/v1/community/posts/${id}`)
}

export function listCommunityComments(postId, params) {
  return http.get(`/api/v1/community/posts/${postId}/comments`, { params })
}

export function createCommunityComment(postId, payload) {
  return http.post(`/api/v1/community/posts/${postId}/comments`, payload)
}

export function likeCommunityPost(id) {
  return http.post(`/api/v1/community/posts/${id}/like`)
}

export function unlikeCommunityPost(id) {
  return http.post(`/api/v1/community/posts/${id}/unlike`)
}

export function favoriteCommunityPost(id) {
  return http.post(`/api/v1/community/posts/${id}/favorite`)
}

export function unfavoriteCommunityPost(id) {
  return http.post(`/api/v1/community/posts/${id}/unfavorite`)
}

export function listMyCommunityPosts(params) {
  return http.get('/api/v1/community/my/posts', { params })
}

export function listAdminCommunityPosts(params) {
  return http.get('/api/v1/admin/community/posts', { params })
}

export function moderateCommunityPost(id, payload) {
  return http.post(`/api/v1/admin/community/posts/${id}/moderation`, payload)
}

export function moderateCommunityComment(id, payload) {
  return http.post(`/api/v1/admin/community/comments/${id}/moderation`, payload)
}
