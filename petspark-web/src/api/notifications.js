import http from './http'

export function listNotifications(params) {
  return http.get('/api/v1/notifications', { params })
}

export function markNotificationRead(id) {
  return http.put(`/api/v1/notifications/${id}/read`)
}

export function markAllNotificationsRead() {
  return http.put('/api/v1/notifications/read-all')
}
