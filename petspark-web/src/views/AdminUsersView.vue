<template>
  <section class="admin-console-page admin-users-view">
    <AdminPageHeader
      eyebrow="账号与权限"
      title="用户和角色"
      description="查看账号状态、分配岗位角色，并用清晰的业务分组维护权限。"
    >
      <template #actions>
        <el-button type="primary" @click="showCreateRole = true">新建自定义角色</el-button>
      </template>
    </AdminPageHeader>

    <el-tabs v-model="activeTab" class="governance-tabs">
      <el-tab-pane label="账号管理" name="accounts">
        <AdminTableShell title="用户账号" :total="total">
          <template #filters>
            <el-input
              v-model.trim="filters.keyword"
              clearable
              placeholder="搜索用户名、邮箱或昵称"
              data-testid="user-keyword"
              @keyup.enter.native="reload"
            />
            <el-select v-model="filters.status" clearable placeholder="全部状态">
              <el-option label="正常" value="ACTIVE" />
              <el-option label="禁用" value="DISABLED" />
              <el-option label="锁定" value="LOCKED" />
            </el-select>
            <el-button type="primary" :loading="loading" @click="reload">查询</el-button>
          </template>

          <el-table v-loading="loading" :data="users" data-testid="admin-user-table" empty-text="暂无用户">
            <el-table-column label="用户" min-width="220">
              <template #default="{ row }">
                <div class="identity-cell">
                  <strong>{{ row.nickname || row.username }}</strong>
                  <span>@{{ row.username }} · {{ row.email || '未填写邮箱' }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="账号状态" width="120">
              <template #default="{ row }">
                <StatusTag :status="row.status" :label="statusLabel(row.status)" />
              </template>
            </el-table-column>
            <el-table-column label="当前角色" min-width="300">
              <template #default="{ row }">
                <div class="role-cell">
                  <div class="role-tags">
                    <el-tag
                      v-for="code in row.roleCodes"
                      :key="code"
                      size="small"
                      :type="roleMeta(code).tone"
                      effect="plain"
                    >{{ roleMeta(code).label }}</el-tag>
                    <span v-if="!row.roleCodes.length" class="empty-copy">尚未分配</span>
                  </div>
                  <el-button size="mini" plain @click="openRoleAssignment(row)">调整角色</el-button>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="账号操作" width="120" align="right">
              <template #default="{ row }">
                <el-button
                  v-if="row.status === 'ACTIVE'"
                  size="mini"
                  type="warning"
                  plain
                  @click="requestStatusChange(row, 'DISABLED')"
                >禁用</el-button>
                <el-button v-else size="mini" type="success" plain @click="requestStatusChange(row, 'ACTIVE')">启用</el-button>
              </template>
            </el-table-column>
          </el-table>

          <template #pagination>
            <el-pagination
              background
              layout="prev, pager, next"
              :current-page="page"
              :page-size="size"
              :total="total"
              @current-change="onPageChange"
            />
          </template>
        </AdminTableShell>
      </el-tab-pane>

      <el-tab-pane label="角色与权限" name="roles">
        <div class="role-overview" data-testid="role-overview">
          <article v-for="role in roles" :key="role.code" class="role-card">
            <header class="role-card__header">
              <div>
                <div class="role-title-line">
                  <h2>{{ roleMeta(role.code, role.name).label }}</h2>
                  <el-tag v-if="role.builtIn" size="mini" type="info">系统内置</el-tag>
                </div>
                <p>{{ roleMeta(role.code, role.name).description }}</p>
                <details class="technical-details"><summary>技术信息</summary><code>{{ role.code }}</code></details>
              </div>
              <el-button v-if="!role.builtIn" size="small" plain @click="openPermissionEditor(role)">编辑权限</el-button>
            </header>
            <div class="role-card__summary">
              <strong>{{ role.permissionCodes.length }}</strong>
              <span>项权限，覆盖 {{ roleResourceCount(role) }} 个业务模块</span>
            </div>
            <div class="permission-summary">
              <div v-for="group in rolePermissionGroups(role)" :key="group.resource" class="permission-summary__group">
                <span>{{ group.label }}</span>
                <small>{{ group.permissions.length }} 项</small>
              </div>
              <span v-if="!role.permissionCodes.length" class="empty-copy">尚未配置权限</span>
            </div>
            <p v-if="role.builtIn" class="protected-note">系统内置角色受保护，避免误改导致平台不可用。</p>
          </article>
        </div>
      </el-tab-pane>
    </el-tabs>

    <el-drawer
      title="调整用户角色"
      :visible.sync="roleAssignmentVisible"
      append-to-body
      size="min(440px, 100%)"
      custom-class="role-assignment-drawer"
    >
      <div class="drawer-body">
        <div v-if="roleAssignmentUser" class="drawer-user">
          <strong>{{ roleAssignmentUser.nickname || roleAssignmentUser.username }}</strong>
          <span>@{{ roleAssignmentUser.username }}</span>
        </div>
        <p class="drawer-help">选择该账号承担的职责。更改只有在点击“保存角色”后才会生效。</p>
        <el-alert
          v-if="roleAssignmentChangesAdmin"
          title="正在变更平台管理员权限"
          description="平台管理员可以访问全部管理能力。保存前请再次确认账号职责，避免误授权或失去管理入口。"
          type="warning"
          :closable="false"
          show-icon
        />
        <el-checkbox-group v-model="roleAssignmentForm.roleCodes" class="role-choice-list">
          <div v-for="role in roles" :key="role.code" class="role-choice">
            <el-checkbox :label="role.code">
              <span class="role-choice__name">{{ roleMeta(role.code, role.name).label }}</span>
            </el-checkbox>
            <span>{{ roleMeta(role.code, role.name).description }}</span>
            <details class="technical-details"><summary>技术信息</summary><code>{{ role.code }}</code></details>
          </div>
        </el-checkbox-group>
        <div class="drawer-actions">
          <el-button @click="roleAssignmentVisible = false">取消</el-button>
          <el-button type="primary" :loading="savingRoleAssignment" @click="saveRoleAssignment">保存角色</el-button>
        </div>
      </div>
    </el-drawer>

    <el-drawer
      title="编辑角色权限"
      :visible.sync="permissionEditorVisible"
      append-to-body
      size="min(560px, 100%)"
      custom-class="permission-editor-drawer"
    >
      <div class="drawer-body">
        <template v-if="permissionEditorRole">
          <div class="drawer-user">
            <strong>{{ roleMeta(permissionEditorRole.code, permissionEditorRole.name).label }}</strong>
            <span>{{ permissionEditorForm.permissionCodes.length }} 项已选权限</span>
          </div>
          <PermissionGroupPicker v-model="permissionEditorForm.permissionCodes" :groups="permissionGroups" />
          <div class="drawer-actions">
            <el-button @click="permissionEditorVisible = false">取消</el-button>
            <el-button type="primary" :loading="savingPermissions" @click="savePermissionEditor">保存权限</el-button>
          </div>
        </template>
      </div>
    </el-drawer>

    <el-dialog
      title="新建自定义角色"
      :visible.sync="showCreateRole"
      append-to-body
      width="min(680px, calc(100vw - 32px))"
    >
      <el-form label-position="top" @submit.native.prevent="submitRole">
        <div class="role-form-grid">
          <el-form-item label="角色编码">
            <el-input v-model.trim="roleForm.code" placeholder="例如 CUSTOM_OP" />
            <span class="field-help">供系统识别，建议使用大写字母和下划线。</span>
          </el-form-item>
          <el-form-item label="角色名称">
            <el-input v-model.trim="roleForm.name" placeholder="例如 内容运营专员" />
          </el-form-item>
        </div>
        <el-form-item label="权限范围">
          <PermissionGroupPicker v-model="roleForm.permissionCodes" :groups="permissionGroups" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateRole = false">取消</el-button>
        <el-button type="primary" :loading="creatingRole" @click="submitRole">创建角色</el-button>
      </template>
    </el-dialog>

    <ConfirmActionDialog
      :visible.sync="statusDialogVisible"
      :title="pendingStatus === 'DISABLED' ? '禁用用户' : '启用用户'"
      :description="pendingStatus === 'DISABLED' ? '确认禁用该用户账号吗？' : '确认恢复该用户账号吗？'"
      :warning="pendingStatus === 'DISABLED' ? '禁用后该用户将无法继续登录。' : ''"
      :confirm-type="pendingStatus === 'DISABLED' ? 'danger' : 'primary'"
      :confirm-text="pendingStatus === 'DISABLED' ? '确认禁用' : '确认启用'"
      :loading="changingStatus"
      @confirm="confirmStatusChange"
    />
  </section>
</template>

<script>
import {
  assignAdminUserRoles,
  createRole,
  listAdminUsers,
  listPermissions,
  listRoles,
  updateAdminUserStatus,
  updateRolePermissions
} from '@/api/admin'
import AdminPageHeader from '@/components/ui/AdminPageHeader.vue'
import AdminTableShell from '@/components/ui/AdminTableShell.vue'
import ConfirmActionDialog from '@/components/ui/ConfirmActionDialog.vue'
import PermissionGroupPicker from '@/components/ui/PermissionGroupPicker.vue'
import StatusTag from '@/components/ui/StatusTag.vue'
import { getPermissionLabel, getResourceLabel, getRoleMeta } from '@/utils/adminTerminology'

const DEFAULT_PAGE_SIZE = 10

export default {
  name: 'AdminUsersView',
  components: { AdminPageHeader, AdminTableShell, ConfirmActionDialog, PermissionGroupPicker, StatusTag },
  data() {
    return {
      activeTab: 'accounts',
      loading: false,
      users: [],
      roles: [],
      permissions: [],
      filters: { keyword: '', status: '' },
      page: 1,
      size: DEFAULT_PAGE_SIZE,
      total: 0,
      showCreateRole: false,
      creatingRole: false,
      roleForm: { code: '', name: '', permissionCodes: [] },
      roleAssignmentVisible: false,
      roleAssignmentUser: null,
      roleAssignmentForm: { roleCodes: [] },
      savingRoleAssignment: false,
      permissionEditorVisible: false,
      permissionEditorRole: null,
      permissionEditorForm: { permissionCodes: [] },
      savingPermissions: false,
      statusDialogVisible: false,
      changingStatus: false,
      pendingStatus: '',
      pendingStatusUser: null
    }
  },
  computed: {
    roleAssignmentChangesAdmin() {
      if (!this.roleAssignmentUser) return false
      const hadAdmin = (this.roleAssignmentUser.roleCodes || []).includes('ADMIN')
      const hasAdmin = (this.roleAssignmentForm.roleCodes || []).includes('ADMIN')
      return hadAdmin !== hasAdmin
    },
    permissionGroups() {
      const groups = this.permissions.reduce((result, permission) => {
        const resource = permission.resource || 'other'
        if (!result[resource]) result[resource] = []
        result[resource].push(permission)
        return result
      }, {})
      return Object.keys(groups).sort().map(resource => ({
        resource,
        label: getResourceLabel(resource),
        permissions: groups[resource].slice().sort((a, b) => a.code.localeCompare(b.code))
      }))
    }
  },
  created() { this.loadAll() },
  methods: {
    async loadAll() { await Promise.all([this.loadRolesAndPermissions(), this.loadUsers()]) },
    async loadRolesAndPermissions() {
      const [roles, permissions] = await Promise.all([listRoles(), listPermissions()])
      this.roles = (roles.data || []).map(role => ({ ...role, permissionCodes: [...(role.permissionCodes || [])] }))
      this.permissions = permissions.data || []
    },
    async loadUsers() {
      this.loading = true
      try {
        const response = await listAdminUsers({
          keyword: this.filters.keyword || undefined,
          status: this.filters.status || undefined,
          page: this.page,
          size: this.size
        })
        const page = response.data
        this.users = (page.items || []).map(user => ({ ...user, roleCodes: [...(user.roleCodes || [])] }))
        this.page = page.page
        this.size = page.size
        this.total = page.total
      } catch (err) { this.$message.error(err.message) } finally { this.loading = false }
    },
    reload() { this.page = 1; this.loadUsers() },
    onPageChange(next) { this.page = next; this.loadUsers() },
    async changeStatus(user, status) {
      try {
        const response = await updateAdminUserStatus(user.id, { status, version: user.version })
        Object.assign(user, response.data)
        this.$message.success('用户状态已更新')
      } catch (err) { this.$message.error(err.message); this.loadUsers() }
    },
    requestStatusChange(user, status) {
      this.pendingStatusUser = user
      this.pendingStatus = status
      this.statusDialogVisible = true
    },
    async confirmStatusChange() {
      if (!this.pendingStatusUser || !this.pendingStatus) return
      this.changingStatus = true
      try {
        await this.changeStatus(this.pendingStatusUser, this.pendingStatus)
        this.statusDialogVisible = false
        this.pendingStatusUser = null
        this.pendingStatus = ''
      } finally { this.changingStatus = false }
    },
    openRoleAssignment(user) {
      this.roleAssignmentUser = user
      this.roleAssignmentForm = { roleCodes: [...(user.roleCodes || [])] }
      this.roleAssignmentVisible = true
    },
    async saveRoleAssignment() {
      if (!this.roleAssignmentUser) return
      if (this.roleAssignmentChangesAdmin) {
        try {
          await this.$confirm(
            '平台管理员拥有全部管理权限。请确认本次授权或移除符合账号职责。',
            '高风险角色变更',
            { type: 'warning', confirmButtonText: '确认变更', cancelButtonText: '返回检查' }
          )
        } catch (err) { return }
      }
      this.savingRoleAssignment = true
      try {
        await this.assignRoles(this.roleAssignmentUser, this.roleAssignmentForm.roleCodes)
        this.roleAssignmentVisible = false
      } catch (err) {
        // assignRoles 已展示错误并重新加载服务端状态；抽屉保持打开便于修正。
      } finally { this.savingRoleAssignment = false }
    },
    async assignRoles(user, roleCodes = user.roleCodes) {
      try {
        const response = await assignAdminUserRoles(user.id, { roleCodes: [...roleCodes] })
        Object.assign(user, response.data, { roleCodes: [...(response.data.roleCodes || [])] })
        this.$message.success('用户角色已保存')
      } catch (err) { this.$message.error(err.message); await this.loadUsers(); throw err }
    },
    async submitRole() {
      this.creatingRole = true
      try {
        await createRole({ ...this.roleForm, permissionCodes: [...this.roleForm.permissionCodes] })
        this.roleForm = { code: '', name: '', permissionCodes: [] }
        this.showCreateRole = false
        await this.loadRolesAndPermissions()
        this.$message.success('角色已创建')
      } catch (err) { this.$message.error(err.message) } finally { this.creatingRole = false }
    },
    openPermissionEditor(role) {
      this.permissionEditorRole = role
      this.permissionEditorForm = { permissionCodes: [...role.permissionCodes] }
      this.permissionEditorVisible = true
    },
    async savePermissionEditor() {
      if (!this.permissionEditorRole) return
      this.permissionEditorRole.permissionCodes = [...this.permissionEditorForm.permissionCodes]
      this.savingPermissions = true
      try {
        await this.updatePermissions(this.permissionEditorRole)
        this.permissionEditorVisible = false
      } finally { this.savingPermissions = false }
    },
    async updatePermissions(role) {
      try {
        const response = await updateRolePermissions(role.code, { permissionCodes: role.permissionCodes })
        Object.assign(role, response.data, { permissionCodes: [...(response.data.permissionCodes || [])] })
        this.$message.success('角色权限已保存')
      } catch (err) { this.$message.error(err.message); await this.loadRolesAndPermissions(); throw err }
    },
    roleMeta(code, fallbackName) {
      const role = this.roles.find(item => item.code === code)
      return getRoleMeta(code, fallbackName || (role && role.name))
    },
    permissionLabel(permission) { return getPermissionLabel(permission) },
    rolePermissionGroups(role) {
      const selected = new Set(role.permissionCodes || [])
      return this.permissionGroups.map(group => ({
        ...group,
        permissions: group.permissions.filter(permission => selected.has(permission.code))
      })).filter(group => group.permissions.length)
    },
    roleResourceCount(role) { return this.rolePermissionGroups(role).length },
    statusLabel(status) { return { ACTIVE: '正常', DISABLED: '禁用', LOCKED: '锁定' }[status] || status }
  }
}
</script>

<style scoped>
.admin-console-page { display: grid; gap: 20px; }
.governance-tabs { min-width: 0; }
.governance-tabs ::v-deep .el-tabs__header { margin: 0 0 18px; }
.governance-tabs ::v-deep .el-tabs__item { height: 48px; padding: 0 24px; font-size: 15px; font-weight: 700; }
.identity-cell, .drawer-user { display: grid; gap: 5px; }
.identity-cell strong, .drawer-user strong { color: var(--ps-color-text); }
.identity-cell span, .drawer-user span, .empty-copy { color: var(--ps-color-muted); font-size: 13px; }
.role-cell { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.role-tags { display: flex; flex-wrap: wrap; gap: 6px; }
.role-overview { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; }
.role-card { padding: 20px; background: var(--ps-color-surface); border: 1px solid var(--ps-color-border); border-radius: var(--ps-radius-md); box-shadow: var(--ps-shadow-sm); }
.role-card__header { display: flex; align-items: flex-start; justify-content: space-between; gap: 20px; }
.role-title-line { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; }
.role-card h2 { margin: 0; color: var(--ps-color-text); font-size: 18px; }
.role-card p { margin: 7px 0; color: var(--ps-color-muted); line-height: 1.6; }
.role-card code, .role-choice code { color: var(--ps-color-muted); font-size: 12px; }
.role-card__summary { display: flex; align-items: baseline; gap: 6px; margin: 18px 0 12px; padding-top: 16px; border-top: 1px solid var(--ps-color-border); color: var(--ps-color-muted); }
.role-card__summary strong { color: var(--ps-color-blue); font-size: 26px; }
.permission-summary { display: flex; flex-wrap: wrap; gap: 8px; }
.permission-summary__group { display: inline-flex; align-items: center; gap: 6px; padding: 6px 9px; background: var(--ps-color-surface-soft); border-radius: 8px; color: var(--ps-color-text); font-size: 13px; }
.permission-summary__group small { color: var(--ps-color-muted); }
.protected-note { margin-bottom: 0 !important; font-size: 12px; }
.drawer-body { display: grid; gap: 18px; min-height: calc(100vh - 96px); padding: 0 22px 24px; }
.drawer-help { margin: 0; color: var(--ps-color-muted); line-height: 1.7; }
.role-choice-list { display: grid; align-content: start; gap: 10px; }
.role-choice { display: grid; gap: 6px; padding: 14px; border: 1px solid var(--ps-color-border); border-radius: 12px; }
.role-choice:hover { border-color: var(--ps-color-blue); background: var(--ps-color-surface-soft); }
.role-choice > span { padding-left: 25px; color: var(--ps-color-muted); font-size: 13px; line-height: 1.5; }
.role-choice > code { padding-left: 25px; }
.role-choice__name { color: var(--ps-color-text); font-weight: 700; }
.drawer-actions { align-self: end; display: flex; justify-content: flex-end; gap: 10px; position: sticky; bottom: 0; padding-top: 16px; background: var(--ps-color-surface); border-top: 1px solid var(--ps-color-border); }
.role-form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; }
.field-help { display: block; margin-top: 5px; color: var(--ps-color-muted); font-size: 12px; line-height: 1.5; }
.technical-details { color: var(--ps-color-muted); font-size: 12px; }
.technical-details summary { cursor: pointer; list-style-position: inside; }
.technical-details code { display: block; margin-top: 4px; padding-left: 14px; opacity: .8; }
@media (max-width: 980px) { .role-overview { grid-template-columns: 1fr; } }
@media (max-width: 640px) { .role-cell, .role-card__header { align-items: flex-start; flex-direction: column; } .role-form-grid { grid-template-columns: 1fr; } }
</style>
