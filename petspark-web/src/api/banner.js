import http from '@/api/http'

export function listActiveBanners(params) {
  return http.get('/api/v1/banners', { params })
}

export function listAdminBanners(params) {
  return http.get('/api/v1/admin/banners', { params })
}

export function getAdminBanner(id) {
  return http.get(`/api/v1/admin/banners/${id}`)
}

export function createBanner(payload) {
  return http.post('/api/v1/admin/banners', payload)
}

export function updateBanner(id, payload) {
  return http.put(`/api/v1/admin/banners/${id}`, payload)
}

export function updateBannerStatus(id, payload) {
  return http.patch(`/api/v1/admin/banners/${id}/status`, payload)
}

export function updateBannerOrder(id, payload) {
  return http.patch(`/api/v1/admin/banners/${id}/order`, payload)
}

export function deleteBanner(id, version) {
  return http.delete(`/api/v1/admin/banners/${id}`, { params: { version } })
}
