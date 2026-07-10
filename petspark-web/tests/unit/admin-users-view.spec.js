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
    'el-alert': true,
    'el-button': true,
    'el-card': true,
    'el-dialog': true,
    'el-drawer': true,
    'el-checkbox': true,
    'el-checkbox-group': true,
    'el-collapse': true,
    'el-collapse-item': true,
    'el-form': true,
    'el-form-item': true,
    'el-input': true,
    'el-option': true,
    'el-pagination': true,
    'el-select': true,
    'el-tabs': true,
    'el-tab-pane': true,
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
      mocks: {
        $message: { success: jest.fn(), error: jest.fn() },
        $confirm: jest.fn().mockResolvedValue('confirm')
      }
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

  it('stages role assignment in a drawer and saves only after confirmation', async () => {
    const wrapper = mountView()
    await flush()

    wrapper.vm.openRoleAssignment(wrapper.vm.users[0])

    expect(wrapper.vm.roleAssignmentVisible).toBe(true)
    expect(wrapper.vm.roleAssignmentForm.roleCodes).toEqual(['ADMIN'])
    expect(assignAdminUserRoles).not.toHaveBeenCalled()

    wrapper.vm.roleAssignmentForm.roleCodes = ['ADMIN', 'OP']
    await wrapper.vm.saveRoleAssignment()

    expect(assignAdminUserRoles).toHaveBeenCalledWith('u-1', { roleCodes: ['ADMIN', 'OP'] })
    expect(wrapper.vm.roleAssignmentVisible).toBe(false)
  })

  it('presents Chinese role terminology and groups permissions by resource', async () => {
    listPermissions.mockResolvedValue({
      data: [
        permission,
        { code: 'user:update', resource: 'user', action: 'update', description: '修改用户' },
        { code: 'goods:read', resource: 'goods', action: 'read', description: '查看商品' }
      ]
    })
    const wrapper = mountView()
    await flush()

    expect(wrapper.vm.roleMeta('ADMIN').label).toBe('平台管理员')
    expect(wrapper.vm.permissionGroups.map(group => group.resource)).toEqual(['goods', 'user'])
    expect(wrapper.vm.permissionGroups.find(group => group.resource === 'user').permissions).toHaveLength(2)
    expect(wrapper.vm.permissionLabel(permission)).toBe('查看用户')
  })

  it('uses the custom role name instead of exposing its technical code in the user list', async () => {
    listRoles.mockResolvedValue({
      data: [role, { id: 'r-2', code: 'CUSTOM_OP', name: '内容运营专员', builtIn: false, permissionCodes: [] }]
    })
    const wrapper = mountView()
    await flush()

    expect(wrapper.vm.roleMeta('CUSTOM_OP').label).toBe('内容运营专员')
  })

  it('requires an explicit warning confirmation before adding the administrator role', async () => {
    listAdminUsers.mockResolvedValue({
      data: { items: [{ ...user, roleCodes: ['USER'] }], page: 1, size: 10, total: 1 }
    })
    const wrapper = mountView()
    await flush()

    wrapper.vm.openRoleAssignment(wrapper.vm.users[0])
    wrapper.vm.roleAssignmentForm.roleCodes = ['USER', 'ADMIN']
    await wrapper.vm.saveRoleAssignment()

    expect(wrapper.vm.$confirm).toHaveBeenCalledWith(
      expect.stringContaining('平台管理员'),
      '高风险角色变更',
      expect.objectContaining({ type: 'warning' })
    )
    expect(assignAdminUserRoles).toHaveBeenCalledWith('u-1', { roleCodes: ['USER', 'ADMIN'] })
  })
})

function flush() {
  return new Promise(resolve => setTimeout(resolve, 0))
}
