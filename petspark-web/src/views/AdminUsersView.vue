<template>
  <section class="admin-users-view">
    <header class="admin-users-view__head">
      <div>
        <h2>用户与角色管理</h2>
        <p>管理用户状态、角色分配和自定义角色权限。</p>
      </div>
      <el-button size="small" type="primary" @click="showCreateRole = true">
        新建角色
      </el-button>
    </header>

    <el-card class="admin-card">
      <div class="admin-users-view__toolbar">
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
      </div>

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
            <el-tag :type="statusTag(row.status)">{{ statusLabel(row.status) }}</el-tag>
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
              @click="changeStatus(row, 'DISABLED')"
            >
              禁用
            </el-button>
            <el-button
              v-else
              size="mini"
              type="success"
              @click="changeStatus(row, 'ACTIVE')"
            >
              启用
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <footer class="admin-users-view__pager">
        <el-pagination
          background
          layout="prev, pager, next"
          :current-page="page"
          :page-size="size"
          :total="total"
          @current-change="onPageChange"
        />
      </footer>
    </el-card>

    <el-card class="admin-card">
      <h3>角色权限</h3>
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
    </el-card>

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

const DEFAULT_PAGE_SIZE = 10

export default {
  name: 'AdminUsersView',
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
      }
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
.admin-users-view {
  margin: 24px;
}

.admin-users-view__head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;
}

.admin-users-view__head h2,
.admin-users-view__head p {
  margin: 0;
}

.admin-users-view__head p {
  margin-top: 6px;
  color: #606266;
}

.admin-card {
  margin-bottom: 18px;
}

.admin-users-view__toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}

.admin-users-view__pager {
  margin-top: 16px;
  text-align: right;
}

.hint {
  margin-left: 10px;
  color: #909399;
  font-size: 12px;
}
</style>
