import http from '@/api/http'
import {
  assignAdminUserRoles,
  createRole,
  listAdminUsers,
  listPermissions,
  listRoles,
  updateAdminUserStatus,
  updateRolePermissions
} from '@/api/admin'

jest.mock('@/api/http', () => ({
  get: jest.fn(),
  post: jest.fn(),
  put: jest.fn()
}))

describe('admin API', () => {
  beforeEach(() => {
    http.get.mockReset()
    http.post.mockReset()
    http.put.mockReset()
  })

  it('lists users with query params', () => {
    const params = { keyword: 'yang', status: 'ACTIVE', page: 1, size: 10 }
    listAdminUsers(params)
    expect(http.get).toHaveBeenCalledWith('/api/v1/admin/users', { params })
  })

  it('updates user status and roles', () => {
    updateAdminUserStatus('u-1', { status: 'DISABLED', version: 2 })
    assignAdminUserRoles('u-1', { roleCodes: ['ADMIN'] })

    expect(http.put).toHaveBeenCalledWith('/api/v1/admin/users/u-1/status', { status: 'DISABLED', version: 2 })
    expect(http.put).toHaveBeenCalledWith('/api/v1/admin/users/u-1/roles', { roleCodes: ['ADMIN'] })
  })

  it('manages roles and permissions', () => {
    listRoles()
    listPermissions()
    createRole({ code: 'CUSTOM_OP', name: '自定义运营', permissionCodes: ['user:read'] })
    updateRolePermissions('CUSTOM_OP', { permissionCodes: ['user:read', 'role:read'] })

    expect(http.get).toHaveBeenCalledWith('/api/v1/admin/roles')
    expect(http.get).toHaveBeenCalledWith('/api/v1/admin/permissions')
    expect(http.post).toHaveBeenCalledWith('/api/v1/admin/roles', {
      code: 'CUSTOM_OP',
      name: '自定义运营',
      permissionCodes: ['user:read']
    })
    expect(http.put).toHaveBeenCalledWith('/api/v1/admin/roles/CUSTOM_OP/permissions', {
      permissionCodes: ['user:read', 'role:read']
    })
  })
})
