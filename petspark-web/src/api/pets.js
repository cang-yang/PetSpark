import http from '@/api/http'

export function listPets(params) {
  return http.get('/api/v1/pets', { params })
}

export function getPet(id) {
  return http.get(`/api/v1/pets/${id}`)
}

export function createMyPet(payload) {
  return http.post('/api/v1/pets/mine', payload)
}

export function updateMyPet(id, payload) {
  return http.put(`/api/v1/pets/mine/${id}`, payload)
}

export function listBreeds(params) {
  return http.get('/api/v1/breeds', { params })
}

export function listAdminPets(params) {
  return http.get('/api/v1/admin/pets', { params })
}

export function createAdminPet(payload) {
  return http.post('/api/v1/admin/pets', payload)
}

export function updateAdminPet(id, payload) {
  return http.put(`/api/v1/admin/pets/${id}`, payload)
}

export function updateAdminPetStatus(id, payload) {
  return http.patch(`/api/v1/admin/pets/${id}/status`, payload)
}

export function batchUpdateAdminPetStatus(payload) {
  return http.post('/api/v1/admin/pets:batch-status', payload)
}

export function createAdminBreed(payload) {
  return http.post('/api/v1/admin/breeds', payload)
}

export function updateAdminBreed(id, payload) {
  return http.put(`/api/v1/admin/breeds/${id}`, payload)
}
