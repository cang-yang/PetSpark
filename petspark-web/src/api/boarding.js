import http from '@/api/http'

/**
 * 寄养模块 API 客户端。覆盖房间可用性查询、预约创建/取消/履约、后台房间 CRUD
 * 与预约分配/状态流转。对应后端 BoardingController（API-BOARD-001~007、API-ROOM-001~003）。
 */

// ---------- 用户端 ----------

export function queryAvailability(params) {
  return http.get('/api/v1/boarding/availability', { params })
}

export function createBooking(payload, idempotencyKey) {
  const headers = idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : {}
  return http.post('/api/v1/boarding-bookings', payload, { headers })
}

export function listMyBookings(params) {
  return http.get('/api/v1/boarding-bookings/mine', { params })
}

export function cancelBooking(id, payload) {
  return http.post(`/api/v1/boarding-bookings/${id}/cancel`, payload)
}

// ---------- 后台：房间资源 ----------

export function listRooms(params) {
  return http.get('/api/v1/admin/boarding-rooms', { params })
}

export function createRoom(payload) {
  return http.post('/api/v1/admin/boarding-rooms', payload)
}

export function updateRoom(id, payload) {
  return http.put(`/api/v1/admin/boarding-rooms/${id}`, payload)
}

// ---------- 后台：预约履约 ----------

export function listAdminBookings(params) {
  return http.get('/api/v1/admin/boarding-bookings', { params })
}

export function assignRoom(id, payload) {
  return http.post(`/api/v1/admin/boarding-bookings/${id}/assign`, payload)
}

export function transitionBooking(id, payload) {
  return http.post(`/api/v1/admin/boarding-bookings/${id}/transition`, payload)
}
