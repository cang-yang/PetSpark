import http from '@/api/http'

/** 训练服务 API：thin wrapper 到后端 /training，底层复用 service_booking 状态机。 */

export function listTrainingItems(params) {
  return http.get('/api/v1/training/items', { params })
}

export function getTrainingItem(id) {
  return http.get(`/api/v1/training/items/${id}`)
}

export function listTrainingResources(params) {
  return http.get('/api/v1/training/resources', { params })
}

export function listTrainingSlots(params) {
  return http.get('/api/v1/training/slots', { params })
}

export function createTrainingBooking(payload) {
  return http.post('/api/v1/training/bookings', payload)
}

export function listMyTrainingBookings(params) {
  return http.get('/api/v1/training/bookings', { params })
}

export function getTrainingBooking(id) {
  return http.get(`/api/v1/training/bookings/${id}`)
}

export function cancelTrainingBooking(id, payload) {
  return http.post(`/api/v1/training/bookings/${id}/cancel`, payload)
}

export function exceptionTrainingBooking(id, payload) {
  return http.post(`/api/v1/training/bookings/${id}/exception`, payload)
}

export function listAdminTrainingBookings(params) {
  return http.get('/api/v1/admin/training/bookings', { params })
}

export function transitionTrainingBooking(id, payload) {
  return http.post(`/api/v1/admin/training/bookings/${id}/transition`, payload)
}

export function createTrainingItem(payload) {
  return http.post('/api/v1/admin/training/items', payload)
}

export function updateTrainingItem(id, payload) {
  return http.put(`/api/v1/admin/training/items/${id}`, payload)
}

export function deleteTrainingItem(id) {
  return http.delete(`/api/v1/admin/training/items/${id}`)
}

export function createTrainingResource(payload) {
  return http.post('/api/v1/admin/training/resources', payload)
}

export function updateTrainingResource(id, payload) {
  return http.put(`/api/v1/admin/training/resources/${id}`, payload)
}

export function createTrainingSlots(payload) {
  return http.post('/api/v1/admin/training/slots', payload)
}
