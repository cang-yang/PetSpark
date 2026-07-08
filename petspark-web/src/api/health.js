import http from '@/api/http'

export function listHealthRecords(petId, params) {
  return http.get(`/api/v1/pets/${petId}/health-records`, { params })
}

export function createHealthRecord(petId, payload) {
  return http.post(`/api/v1/pets/${petId}/health-records`, payload)
}

export function reviseHealthRecord(id, payload) {
  return http.post(`/api/v1/health-records/${id}/revisions`, payload)
}

export function eraseHealthRecord(id, payload) {
  return http.delete(`/api/v1/health-records/${id}/content`, { data: payload })
}