import http from '@/api/http'

export function listAdminUsers(params) {
  return http.get('/api/v1/admin/users', { params })
}

export function getAdminUser(id) {
  return http.get(`/api/v1/admin/users/${id}`)
}

export function updateAdminUserStatus(id, payload) {
  return http.put(`/api/v1/admin/users/${id}/status`, payload)
}

export function assignAdminUserRoles(id, payload) {
  return http.put(`/api/v1/admin/users/${id}/roles`, payload)
}

export function listRoles() {
  return http.get('/api/v1/admin/roles')
}

export function createRole(payload) {
  return http.post('/api/v1/admin/roles', payload)
}

export function updateRolePermissions(code, payload) {
  return http.put(`/api/v1/admin/roles/${code}/permissions`, payload)
}

export function listPermissions() {
  return http.get('/api/v1/admin/permissions')
}
