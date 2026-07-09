import http from '@/api/http'

/**
 * 服务预约 API：服务项目/资源/窗口浏览、通用预约创建与生命周期、后台管理。
 * 对应后端 API-SVC-001~013。
 */

// ===== API-SVC-001 服务项目浏览 =====
export function listServiceItems(params) {
  return http.get('/api/v1/services/items', { params })
}

// ===== API-SVC-002 服务项目详情 =====
export function getServiceItem(id) {
  return http.get(`/api/v1/services/items/${id}`)
}

// ===== API-SVC-003 服务资源浏览 =====
export function listServiceResources(params) {
  return http.get('/api/v1/services/resources', { params })
}

// ===== API-SVC-004 可用窗口查询 =====
export function listServiceSlots(params) {
  return http.get('/api/v1/services/slots', { params })
}

// ===== API-SVC-005 创建预约 =====
export function createServiceBooking(payload) {
  return http.post('/api/v1/services/bookings', payload)
}

// ===== API-SVC-006 我的预约列表 =====
export function listMyServiceBookings(params) {
  return http.get('/api/v1/services/bookings', { params })
}

// ===== API-SVC-007 预约详情 =====
export function getServiceBooking(id) {
  return http.get(`/api/v1/services/bookings/${id}`)
}

// ===== API-SVC-008 取消预约 =====
export function cancelServiceBooking(id, payload) {
  return http.post(`/api/v1/services/bookings/${id}/cancel`, payload)
}

// ===== API-SVC-009 异常终止 =====
export function exceptionServiceBooking(id, payload) {
  return http.post(`/api/v1/services/bookings/${id}/exception`, payload)
}

// ===== API-SVC-010 履约状态流转（后台）=====
export function transitionServiceBooking(id, payload) {
  return http.post(`/api/v1/admin/services/bookings/${id}/transition`, payload)
}

// ===== API-SVC-011 管理员预约列表 =====
export function listAdminServiceBookings(params) {
  return http.get('/api/v1/admin/services/bookings', { params })
}

// ===== API-SVC-012 后台服务项目管理 =====
export function createServiceItem(payload) {
  return http.post('/api/v1/admin/services/items', payload)
}

export function updateServiceItem(id, payload) {
  return http.put(`/api/v1/admin/services/items/${id}`, payload)
}

export function deleteServiceItem(id) {
  return http.delete(`/api/v1/admin/services/items/${id}`)
}

// ===== API-SVC-013 后台服务资源/窗口管理 =====
export function createServiceResource(payload) {
  return http.post('/api/v1/admin/services/resources', payload)
}

export function updateServiceResource(id, payload) {
  return http.put(`/api/v1/admin/services/resources/${id}`, payload)
}

export function createServiceSlots(payload) {
  return http.post('/api/v1/admin/services/slots', payload)
}
