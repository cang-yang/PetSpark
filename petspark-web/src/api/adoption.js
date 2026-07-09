import http from '@/api/http'

/**
 * 领养接口客户端（API-ADOPT-001~008）。
 * 业务页面只调用本模块，不在组件内拼 URL 或操作令牌。
 */

export function listAdoptablePets(params) {
  return http.get('/api/v1/pets/adoptable', { params })
}

export function createAdoptionApplication(payload, idempotencyKey) {
  const headers = idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : {}
  return http.post('/api/v1/adoptions', payload, { headers })
}

export function listMyAdoptions(params) {
  return http.get('/api/v1/adoptions', { params })
}

export function getAdoption(id) {
  return http.get(`/api/v1/adoptions/${id}`)
}

export function withdrawAdoption(id, payload) {
  return http.post(`/api/v1/adoptions/${id}/withdraw`, payload)
}

export function listAdminAdoptions(params) {
  return http.get('/api/v1/admin/adoptions', { params })
}

export function decideAdoption(id, payload) {
  return http.post(`/api/v1/admin/adoptions/${id}/decision`, payload)
}

export function handoverAdoption(id, payload) {
  return http.post(`/api/v1/adoptions/${id}/handover`, payload)
}
