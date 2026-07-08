import http from '@/api/http'

export function listDictTypes() {
  return http.get('/api/v1/admin/dictionaries/types')
}

export function createDictType(payload) {
  return http.post('/api/v1/admin/dictionaries/types', payload)
}

export function listDictItems(typeCode) {
  return http.get(`/api/v1/admin/dictionaries/${typeCode}/items`)
}

export function createDictItem(typeCode, payload) {
  return http.post(`/api/v1/admin/dictionaries/${typeCode}/items`, payload)
}

export function updateDictItem(id, payload) {
  return http.put(`/api/v1/admin/dictionaries/items/${id}`, payload)
}

export function listSystemConfigs() {
  return http.get('/api/v1/admin/configs')
}

export function updateSystemConfig(key, payload) {
  return http.put(`/api/v1/admin/configs/${key}`, payload)
}

export function listAuditLogs(params) {
  return http.get('/api/v1/admin/audits', { params })
}
