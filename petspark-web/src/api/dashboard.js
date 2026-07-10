import http from '@/api/http'

/**
 * 后台仪表盘聚合数据（PR-DASHBOARD-01）。
 * GET /api/v1/admin/dashboard → ApiResponse<DashboardSummaryView>
 */
export function getDashboardSummary() {
  return http.get('/api/v1/admin/dashboard')
}
