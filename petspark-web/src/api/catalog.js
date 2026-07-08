import http from '@/api/http'

export function listGoods(params) {
  return http.get('/api/v1/goods', { params })
}

export function getGoods(id) {
  return http.get(`/api/v1/goods/${id}`)
}

export function listGoodsCategories() {
  return http.get('/api/v1/goods/categories')
}

export function listAdminGoods(params) {
  return http.get('/api/v1/admin/goods', { params })
}

export function getAdminGoods(id) {
  return http.get(`/api/v1/admin/goods/${id}`)
}

export function createGoods(payload) {
  return http.post('/api/v1/admin/goods', payload)
}

export function updateGoods(id, payload) {
  return http.put(`/api/v1/admin/goods/${id}`, payload)
}

export function updateGoodsStatus(id, payload) {
  return http.patch(`/api/v1/admin/goods/${id}/status`, payload)
}

export function adjustGoodsStock(id, payload) {
  return http.post(`/api/v1/admin/goods/${id}/stock-adjustments`, payload)
}

export function listAdminGoodsCategories() {
  return http.get('/api/v1/admin/goods/categories')
}

export function createGoodsCategory(payload) {
  return http.post('/api/v1/admin/goods/categories', payload)
}

export function updateGoodsCategory(id, payload) {
  return http.put(`/api/v1/admin/goods/categories/${id}`, payload)
}
