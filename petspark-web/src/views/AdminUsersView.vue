<template>
  <section class="admin-console-page admin-users-view">
    <AdminPageHeader eyebrow="权限与账号" title="用户与角色管理" description="管理用户状态、角色分配和自定义角色权限。">
      <template #actions><el-button type="primary" @click="showCreateRole = true">新建角色</el-button></template>
    </AdminPageHeader>

    <AdminTableShell title="用户列表" :total="total">
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

      <el-table
        v-loading="loading"
        :data="users"
        data-testid="admin-user-table"
        empty-text="暂无用户"
      >
        <el-table-column prop="username" label="用户名" min-width="140" />
        <el-table-column prop="email" label="邮箱" min-width="180" />
        <el-table-column prop="nickname" label="昵称" min-width="120" />
        <el-table-column prop="status" label="状态" width="110">
          <template #default="{ row }">
            <StatusTag :status="row.status" :label="statusLabel(row.status)" />
          </template>
        </el-table-column>
        <el-table-column label="角色" min-width="220">
          <template #default="{ row }">
            <el-select
              v-model="row.roleCodes"
              multiple
              filterable
              placeholder="选择角色"
              @change="assignRoles(row)"
            >
              <el-option
                v-for="role in roles"
                :key="role.code"
                :label="`${role.name}（${role.code}）`"
                :value="role.code"
              />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'ACTIVE'"
              size="mini"
              type="warning"
              @click="requestStatusChange(row, 'DISABLED')"
            >
              禁用
            </el-button>
            <el-button
              v-else
              size="mini"
              type="success"
              @click="requestStatusChange(row, 'ACTIVE')"
            >
              启用
            </el-button>
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

    <AdminTableShell title="角色权限" :total="roles.length">
      <el-table :data="roles" data-testid="role-table" empty-text="暂无角色">
        <el-table-column prop="code" label="角色编码" width="140" />
        <el-table-column prop="name" label="角色名称" width="160" />
        <el-table-column label="权限">
          <template #default="{ row }">
            <el-select
              v-model="row.permissionCodes"
              multiple
              filterable
              :disabled="row.builtIn"
              placeholder="选择权限"
              @change="updatePermissions(row)"
            >
              <el-option
                v-for="permission in permissions"
                :key="permission.code"
                :label="permission.description || permission.code"
                :value="permission.code"
              />
            </el-select>
            <span v-if="row.builtIn" class="hint">内置角色权限受保护</span>
          </template>
        </el-table-column>
      </el-table>
    </AdminTableShell>

    <el-dialog title="新建自定义角色" :visible.sync="showCreateRole">
      <el-form label-width="96px" @submit.native.prevent="submitRole">
        <el-form-item label="角色编码">
          <el-input v-model.trim="roleForm.code" placeholder="例如 CUSTOM_OP" />
        </el-form-item>
        <el-form-item label="角色名称">
          <el-input v-model.trim="roleForm.name" placeholder="例如 自定义运营" />
        </el-form-item>
        <el-form-item label="权限">
          <el-select v-model="roleForm.permissionCodes" multiple filterable placeholder="选择权限">
            <el-option
              v-for="permission in permissions"
              :key="permission.code"
              :label="permission.description || permission.code"
              :value="permission.code"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateRole = false">取消</el-button>
        <el-button type="primary" :loading="creatingRole" @click="submitRole">创建</el-button>
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
import StatusTag from '@/components/ui/StatusTag.vue'

const DEFAULT_PAGE_SIZE = 10

export default {
  name: 'AdminUsersView',
  components: { AdminPageHeader, AdminTableShell, ConfirmActionDialog, StatusTag },
  data() {
    return {
      loading: false,
      users: [],
      roles: [],
      permissions: [],
      filters: {
        keyword: '',
        status: ''
      },
      page: 1,
      size: DEFAULT_PAGE_SIZE,
      total: 0,
      showCreateRole: false,
      creatingRole: false,
      roleForm: {
        code: '',
        name: '',
        permissionCodes: []
      },
      statusDialogVisible: false,
      changingStatus: false,
      pendingStatus: '',
      pendingStatusUser: null
    }
  },
  created() {
    this.loadAll()
  },
  methods: {
    async loadAll() {
      await Promise.all([this.loadRolesAndPermissions(), this.loadUsers()])
    },
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
      } catch (err) {
        this.$message.error(err.message)
      } finally {
        this.loading = false
      }
    },
    reload() {
      this.page = 1
      this.loadUsers()
    },
    onPageChange(next) {
      this.page = next
      this.loadUsers()
    },
    async changeStatus(user, status) {
      try {
        const response = await updateAdminUserStatus(user.id, { status, version: user.version })
        Object.assign(user, response.data)
        this.$message.success('用户状态已更新')
      } catch (err) {
        this.$message.error(err.message)
        this.loadUsers()
      }
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
      } finally {
        this.changingStatus = false
      }
    },
    async assignRoles(user) {
      try {
        const response = await assignAdminUserRoles(user.id, { roleCodes: user.roleCodes })
        Object.assign(user, response.data, { roleCodes: [...(response.data.roleCodes || [])] })
        this.$message.success('用户角色已更新')
      } catch (err) {
        this.$message.error(err.message)
        this.loadUsers()
      }
    },
    async submitRole() {
      this.creatingRole = true
      try {
        await createRole({
          code: this.roleForm.code,
          name: this.roleForm.name,
          permissionCodes: this.roleForm.permissionCodes
        })
        this.roleForm = { code: '', name: '', permissionCodes: [] }
        this.showCreateRole = false
        await this.loadRolesAndPermissions()
        this.$message.success('角色已创建')
      } catch (err) {
        this.$message.error(err.message)
      } finally {
        this.creatingRole = false
      }
    },
    async updatePermissions(role) {
      try {
        const response = await updateRolePermissions(role.code, { permissionCodes: role.permissionCodes })
        Object.assign(role, response.data, { permissionCodes: [...(response.data.permissionCodes || [])] })
        this.$message.success('角色权限已更新')
      } catch (err) {
        this.$message.error(err.message)
        this.loadRolesAndPermissions()
      }
    },
    statusLabel(status) {
      return { ACTIVE: '正常', DISABLED: '禁用', LOCKED: '锁定' }[status] || status
    },
    statusTag(status) {
      return { ACTIVE: 'success', DISABLED: 'danger', LOCKED: 'warning' }[status] || 'info'
    }
  }
}
</script>

<style scoped>
.admin-console-page { display: grid; gap: 20px; }

.hint {
  margin-left: 10px;
  color: #909399;
  font-size: 12px;
}
</style>
