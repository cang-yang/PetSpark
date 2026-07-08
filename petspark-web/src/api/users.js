import http from '@/api/http'

export function getMyProfile() {
  return http.get('/api/v1/users/me')
}

export function updateMyProfile(payload) {
  return http.put('/api/v1/users/me', payload)
}
