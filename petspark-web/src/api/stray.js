import http from '@/api/http'

/** 流浪救助线索 API 客户端。组件不拼接 URL，统一从本模块调用。 */

export function createStrayClue(payload, idempotencyKey) {
  const headers = idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : {}
  return http.post('/api/v1/stray-clues', payload, { headers })
}

export function listMyStrayClues(params) {
  return http.get('/api/v1/stray-clues/mine', { params })
}

export function getMyStrayClue(id) {
  return http.get(`/api/v1/stray-clues/${id}`)
}

export function listAdminStrayClues(params) {
  return http.get('/api/v1/admin/stray-clues', { params })
}

export function getAdminStrayClue(id) {
  return http.get(`/api/v1/admin/stray-clues/${id}`)
}

export function assignStrayClue(id, payload) {
  return http.post(`/api/v1/admin/stray-clues/${id}/assign`, payload)
}

export function transitionStrayClue(id, payload) {
  return http.post(`/api/v1/admin/stray-clues/${id}/transition`, payload)
}
