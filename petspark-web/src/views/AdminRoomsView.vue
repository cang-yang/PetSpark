<template>
  <section class="admin-console-page admin-rooms">
    <AdminPageHeader eyebrow="寄养资源" title="房间管理" description="维护寄养房间容量与可用状态。">
      <template #actions><el-button type="primary" @click="openCreate" data-testid="admin-rooms-create">新建房间</el-button></template>
    </AdminPageHeader>
    <AdminTableShell title="房间列表" :total="total">
      <template #filters>
      <el-input v-model="filters.keyword" placeholder="房间编码或名称" clearable />
      <el-select v-model="filters.status" placeholder="状态" clearable>
        <el-option label="启用" value="ACTIVE" />
        <el-option label="停用" value="INACTIVE" />
      </el-select>
      <el-button type="primary" @click="search">查询</el-button>
      </template>

    <el-table :data="rooms" data-testid="admin-rooms-table">
      <el-table-column prop="code" label="编码" />
      <el-table-column prop="name" label="名称" />
      <el-table-column prop="capacity" label="容量" width="80" />
      <el-table-column label="状态" width="110"><template #default="{ row }"><StatusTag :status="row.status" :label="row.status === 'ACTIVE' ? '启用' : '停用'" /></template></el-table-column>
      <el-table-column label="操作">
        <template slot-scope="{ row }">
          <el-button size="mini" @click="openEdit(row)">编辑</el-button>
        </template>
      </el-table-column>
    </el-table>
      <template #pagination><el-pagination background layout="prev, pager, next" :current-page="page.page" :page-size="page.size" :total="total" @current-change="changePage" /></template>
    </AdminTableShell>

    <el-dialog :title="editForm.id ? '编辑房间' : '新建房间'" :visible.sync="showEdit" width="480px">
      <el-form :model="editForm" label-width="80px">
        <el-form-item label="编码"><el-input v-model="editForm.code" :disabled="!!editForm.id" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="editForm.name" maxlength="120" /></el-form-item>
        <el-form-item label="容量"><el-input-number v-model="editForm.capacity" :min="1" :max="9999" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="editForm.description" type="textarea" :rows="3" maxlength="500" /></el-form-item>
      </el-form>
      <span slot="footer">
        <el-button @click="showEdit = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitRoom">保存</el-button>
      </span>
    </el-dialog>
  </section>
</template>

<script>
import { listRooms, createRoom, updateRoom } from '@/api/boarding'
import AdminPageHeader from '@/components/ui/AdminPageHeader.vue'
import AdminTableShell from '@/components/ui/AdminTableShell.vue'
import StatusTag from '@/components/ui/StatusTag.vue'

export default {
  name: 'AdminRoomsView',
  components: { AdminPageHeader, AdminTableShell, StatusTag },
  data() {
    return {
      rooms: [],
      total: 0,
      filters: { keyword: undefined, status: undefined },
      page: { page: 1, size: 20 },
      showEdit: false,
      saving: false,
      editForm: this.emptyForm()
    }
  },
  created() {
    this.loadRooms()
  },
  methods: {
    emptyForm() {
      return { id: null, code: '', name: '', capacity: 1, description: '', version: 0 }
    },
    async loadRooms() {
      try {
        const response = await listRooms({
          keyword: this.filters.keyword || undefined,
          status: this.filters.status || undefined,
          page: this.page.page,
          size: this.page.size
        })
        this.rooms = response.data.items || []
        this.total = response.data.total || 0
      } catch (error) {
        this.$message && this.$message.error(error.message)
      }
    },
    search() { this.page.page = 1; this.loadRooms() },
    changePage(page) { this.page.page = page; this.loadRooms() },
    openCreate() {
      this.editForm = this.emptyForm()
      this.showEdit = true
    },
    openEdit(row) {
      this.editForm = { id: row.id, code: row.code, name: row.name, capacity: row.capacity, description: row.description || '', version: row.version }
      this.showEdit = true
    },
    async submitRoom() {
      this.saving = true
      try {
        const payload = {
          code: this.editForm.code,
          name: this.editForm.name,
          capacity: this.editForm.capacity,
          description: this.editForm.description,
          version: this.editForm.version || 0
        }
        if (this.editForm.id) {
          await updateRoom(this.editForm.id, payload)
          this.$message && this.$message.success('房间已更新')
        } else {
          await createRoom(payload)
          this.$message && this.$message.success('房间已创建')
        }
        this.showEdit = false
        await this.loadRooms()
      } catch (error) {
        this.$message && this.$message.error(error.message)
      } finally {
        this.saving = false
      }
    }
  }
}
</script>

<style scoped>
.admin-console-page { display: grid; gap: 20px; }
</style>
