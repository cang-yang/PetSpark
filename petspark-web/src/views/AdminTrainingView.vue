<template>
  <section class="admin-training">
    <h2>训练管理</h2>
    <el-tabs v-model="activeTab">
      <el-tab-pane label="训练项目" name="items">
        <div class="toolbar">
          <el-input v-model="itemFilters.keyword" placeholder="项目名称/编码" clearable />
          <el-button type="primary" @click="loadItems">查询</el-button>
          <el-button type="success" @click="openItemForm()">新增训练项目</el-button>
        </div>
        <el-table :data="items" data-testid="admin-training-items-table">
          <el-table-column prop="code" label="编码" />
          <el-table-column prop="name" label="名称" />
          <el-table-column prop="basePrice" label="基础价" />
          <el-table-column prop="status" label="状态" />
          <el-table-column label="操作" width="260">
            <template slot-scope="{ row }">
              <el-button size="mini" @click="openItemForm(row)">编辑</el-button>
              <el-button size="mini" @click="openResourceForm(row)">新增资源</el-button>
              <el-button size="mini" type="danger" @click="removeItem(row)">停用</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="训练预约" name="bookings">
        <div class="toolbar">
          <el-input v-model="bookingFilters.keyword" placeholder="预约号" clearable />
          <el-select v-model="bookingFilters.status" placeholder="状态" clearable>
            <el-option label="已确认" value="CONFIRMED" />
            <el-option label="训练中" value="IN_PROGRESS" />
            <el-option label="已完成" value="COMPLETED" />
            <el-option label="已取消" value="CANCELLED" />
            <el-option label="异常终止" value="EXCEPTION" />
          </el-select>
          <el-button type="primary" @click="loadBookings">查询</el-button>
        </div>
        <el-table :data="bookings" data-testid="admin-training-bookings-table">
          <el-table-column prop="bookingNo" label="预约号" />
          <el-table-column prop="serviceItemName" label="训练项目" />
          <el-table-column prop="customerName" label="联系人" />
          <el-table-column label="状态">
            <template slot-scope="{ row }"><el-tag :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag></template>
          </el-table-column>
          <el-table-column label="开始时间"><template slot-scope="{ row }">{{ formatTime(row.startAt) }}</template></el-table-column>
          <el-table-column label="操作" width="220">
            <template slot-scope="{ row }">
              <el-button size="mini" :disabled="row.status !== 'CONFIRMED'" @click="transition(row, 'IN_PROGRESS', '开始训练')">开始</el-button>
              <el-button size="mini" type="success" :disabled="row.status !== 'IN_PROGRESS'" @click="transition(row, 'COMPLETED', '完成训练')">完成</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog :title="itemForm.id ? '编辑训练项目' : '新增训练项目'" :visible.sync="showItemDialog" width="640px">
      <el-form :model="itemForm" label-width="100px">
        <el-form-item label="编码"><el-input v-model="itemForm.code" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="itemForm.name" /></el-form-item>
        <el-form-item label="基础价"><el-input v-model.number="itemForm.basePrice" type="number" /></el-form-item>
        <el-form-item label="状态"><el-select v-model="itemForm.status"><el-option label="启用" value="ACTIVE" /><el-option label="停用" value="INACTIVE" /></el-select></el-form-item>
        <el-form-item label="描述"><el-input v-model="itemForm.description" type="textarea" /></el-form-item>
        <el-form-item label="资质"><el-input v-model="itemForm.qualification" type="textarea" /></el-form-item>
        <el-form-item label="时段说明"><el-input v-model="itemForm.availabilityNote" type="textarea" /></el-form-item>
        <el-form-item label="异常规则"><el-input v-model="itemForm.exceptionRule" type="textarea" /></el-form-item>
      </el-form>
      <span slot="footer"><el-button @click="showItemDialog = false">取消</el-button><el-button type="primary" @click="saveItem">保存</el-button></span>
    </el-dialog>

    <el-dialog title="新增训练资源与窗口" :visible.sync="showResourceDialog" width="640px">
      <el-form :model="resourceForm" label-width="110px">
        <el-form-item label="训练项目"><el-input :value="resourceForm.serviceItemName" disabled /></el-form-item>
        <el-form-item label="资源名称"><el-input v-model="resourceForm.name" /></el-form-item>
        <el-form-item label="容量"><el-input v-model.number="resourceForm.capacity" type="number" /></el-form-item>
        <el-form-item label="资质"><el-input v-model="resourceForm.qualification" /></el-form-item>
        <el-form-item label="开始时间"><el-input v-model="resourceForm.startAt" placeholder="2026-07-20T02:00:00Z" /></el-form-item>
        <el-form-item label="结束时间"><el-input v-model="resourceForm.endAt" placeholder="2026-07-20T03:00:00Z" /></el-form-item>
      </el-form>
      <span slot="footer"><el-button @click="showResourceDialog = false">取消</el-button><el-button type="primary" @click="saveResourceAndSlot">保存</el-button></span>
    </el-dialog>
  </section>
</template>

<script>
import {
  listTrainingItems,
  createTrainingItem,
  updateTrainingItem,
  deleteTrainingItem,
  createTrainingResource,
  createTrainingSlots,
  listAdminTrainingBookings,
  transitionTrainingBooking
} from '@/api/training'

export default {
  name: 'AdminTrainingView',
  data() {
    return {
      activeTab: 'items',
      items: [],
      bookings: [],
      itemFilters: { keyword: undefined },
      bookingFilters: { keyword: undefined, status: undefined },
      page: { page: 1, size: 20 },
      showItemDialog: false,
      showResourceDialog: false,
      itemForm: {},
      resourceForm: {}
    }
  },
  created() {
    this.loadItems()
    this.loadBookings()
  },
  methods: {
    async loadItems() {
      const response = await listTrainingItems({ keyword: this.itemFilters.keyword || undefined, page: 1, size: 100 })
      this.items = response.data.items || []
    },
    async loadBookings() {
      const response = await listAdminTrainingBookings({
        keyword: this.bookingFilters.keyword || undefined,
        status: this.bookingFilters.status || undefined,
        page: this.page.page,
        size: this.page.size
      })
      this.bookings = response.data.items || []
    },
    openItemForm(row) {
      this.itemForm = row ? { ...row } : { kind: 'TRAINING', status: 'ACTIVE', basePrice: 0 }
      this.showItemDialog = true
    },
    async saveItem() {
      const payload = { ...this.itemForm, kind: 'TRAINING' }
      if (this.itemForm.id) {
        await updateTrainingItem(this.itemForm.id, payload)
      } else {
        await createTrainingItem(payload)
      }
      this.showItemDialog = false
      await this.loadItems()
      this.$message && this.$message.success('训练项目已保存')
    },
    async removeItem(row) {
      await deleteTrainingItem(row.id)
      await this.loadItems()
      this.$message && this.$message.success('训练项目已停用')
    },
    openResourceForm(item) {
      this.resourceForm = { serviceItemId: item.id, serviceItemName: item.name, name: '', capacity: 1, status: 'ACTIVE' }
      this.showResourceDialog = true
    },
    async saveResourceAndSlot() {
      const resourceResponse = await createTrainingResource({
        serviceItemId: this.resourceForm.serviceItemId,
        name: this.resourceForm.name,
        qualification: this.resourceForm.qualification,
        capacity: this.resourceForm.capacity || 1,
        status: 'ACTIVE'
      })
      if (this.resourceForm.startAt && this.resourceForm.endAt) {
        await createTrainingSlots({
          resourceId: resourceResponse.data.id,
          slots: [{ startAt: this.resourceForm.startAt, endAt: this.resourceForm.endAt, capacity: this.resourceForm.capacity || 1 }]
        })
      }
      this.showResourceDialog = false
      this.$message && this.$message.success('训练资源已保存')
    },
    async transition(row, status, note) {
      const response = await transitionTrainingBooking(row.id, { status, note, version: row.version })
      Object.assign(row, response.data)
      this.$message && this.$message.success('状态已更新')
    },
    statusLabel(status) {
      const labels = { CREATED: '已创建', CONFIRMED: '已确认', IN_PROGRESS: '训练中', COMPLETED: '已完成', CANCELLED: '已取消', EXCEPTION: '异常终止' }
      return labels[status] || status
    },
    statusTagType(status) {
      if (status === 'COMPLETED') return 'success'
      if (status === 'CANCELLED') return 'info'
      if (status === 'EXCEPTION') return 'danger'
      if (status === 'IN_PROGRESS') return 'warning'
      return ''
    },
    formatTime(value) {
      if (!value) return ''
      const date = typeof value === 'string' ? new Date(value) : value
      if (Number.isNaN(date.getTime())) return String(value)
      const pad = (n) => String(n).padStart(2, '0')
      return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
    }
  }
}
</script>

<style scoped>
.admin-training { max-width: 1120px; margin: 24px auto; }
.toolbar { display: flex; gap: 12px; margin-bottom: 16px; }
</style>
