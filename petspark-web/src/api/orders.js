import http from '@/api/http'

export function previewOrder(payload) {
  return http.post('/api/v1/orders/preview', payload)
}

export function createOrder(payload, idempotencyKey) {
  const headers = idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : {}
  return http.post('/api/v1/orders', payload, { headers })
}

export function listMyOrders(params) {
  return http.get('/api/v1/orders', { params })
}

export function getOrder(id) {
  return http.get(`/api/v1/orders/${id}`)
}

export function cancelOrder(id, payload) {
  return http.post(`/api/v1/orders/${id}/cancel`, payload)
}

export function listAdminOrders(params) {
  return http.get('/api/v1/admin/orders', { params })
}

export function transitionOrder(id, payload) {
  return http.post(`/api/v1/admin/orders/${id}/transition`, payload)
}
