import { createLocalVue, shallowMount } from '@vue/test-utils'
import AdminUsersView from '@/views/AdminUsersView.vue'
import {
  assignAdminUserRoles,
  createRole,
  listAdminUsers,
  listPermissions,
  listRoles,
  updateAdminUserStatus,
  updateRolePermissions
} from '@/api/admin'

jest.mock('@/api/admin', () => ({
  assignAdminUserRoles: jest.fn(),
  createRole: jest.fn(),
  listAdminUsers: jest.fn(),
  listPermissions: jest.fn(),
  listRoles: jest.fn(),
  updateAdminUserStatus: jest.fn(),
  updateRolePermissions: jest.fn()
}))

describe('AdminUsersView', () => {
  const localVue = createLocalVue()
  localVue.directive('loading', {})

  const stubs = {
    'el-button': true,
    'el-card': true,
    'el-dialog': true,
    'el-form': true,
    'el-form-item': true,
    'el-input': true,
    'el-option': true,
    'el-pagination': true,
    'el-select': true,
    'el-table': true,
    'el-table-column': true,
    'el-tag': true
  }

  const user = {
    id: 'u-1',
    username: 'admin',
    email: 'admin@example.com',
    nickname: '管理员',
    status: 'ACTIVE',
    roleCodes: ['ADMIN'],
    version: 1
  }

  const role = {
    id: 'r-1',
    code: 'ADMIN',
    name: '管理员',
    builtIn: true,
    status: 'ACTIVE',
    permissionCodes: ['user:read']
  }

  const permission = {
    code: 'user:read',
    resource: 'user',
    action: 'read',
    description: '查看用户'
  }

  beforeEach(() => {
    jest.clearAllMocks()
    listRoles.mockResolvedValue({ data: [role] })
    listPermissions.mockResolvedValue({ data: [permission] })
    listAdminUsers.mockResolvedValue({
      data: { items: [user], page: 1, size: 10, total: 1 }
    })
    updateAdminUserStatus.mockResolvedValue({ data: { ...user, status: 'DISABLED', version: 2 } })
    assignAdminUserRoles.mockResolvedValue({ data: { ...user, roleCodes: ['ADMIN', 'OP'], version: 2 } })
    createRole.mockResolvedValue({ data: { id: 'r-2', code: 'CUSTOM_OP' } })
    updateRolePermissions.mockResolvedValue({ data: { ...role, builtIn: false, permissionCodes: ['user:read'] } })
  })

  function mountView() {
    return shallowMount(AdminUsersView, {
      localVue,
      stubs,
      mocks: { $message: { success: jest.fn(), error: jest.fn() } }
    })
  }

  it('loads users, roles and permissions on creation', async () => {
    const wrapper = mountView()
    await flush()

    expect(listRoles).toHaveBeenCalled()
    expect(listPermissions).toHaveBeenCalled()
    expect(listAdminUsers).toHaveBeenCalledWith({
      keyword: undefined,
      status: undefined,
      page: 1,
      size: 10
    })
    expect(wrapper.vm.users[0].roleCodes).toEqual(['ADMIN'])
    expect(wrapper.vm.roles[0].permissionCodes).toEqual(['user:read'])
  })

  it('updates user status with optimistic version', async () => {
    const wrapper = mountView()
    await flush()

    await wrapper.vm.changeStatus(wrapper.vm.users[0], 'DISABLED')

    expect(updateAdminUserStatus).toHaveBeenCalledWith('u-1', { status: 'DISABLED', version: 1 })
    expect(wrapper.vm.users[0].status).toBe('DISABLED')
  })

  it('assigns roles and creates custom role', async () => {
    const wrapper = mountView()
    await flush()

    wrapper.vm.users[0].roleCodes = ['ADMIN', 'OP']
    await wrapper.vm.assignRoles(wrapper.vm.users[0])

    wrapper.setData({
      roleForm: { code: 'CUSTOM_OP', name: '自定义运营', permissionCodes: ['user:read'] }
    })
    await wrapper.vm.submitRole()

    expect(assignAdminUserRoles).toHaveBeenCalledWith('u-1', { roleCodes: ['ADMIN', 'OP'] })
    expect(createRole).toHaveBeenCalledWith({
      code: 'CUSTOM_OP',
      name: '自定义运营',
      permissionCodes: ['user:read']
    })
    expect(wrapper.vm.showCreateRole).toBe(false)
  })
})

function flush() {
  return new Promise(resolve => setTimeout(resolve, 0))
}
