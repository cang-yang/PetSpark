<template>
  <section class="admin-console-page admin-medical">
    <AdminPageHeader eyebrow="健康服务" title="医疗管理" description="维护医疗服务项目并跟进预约履约。" />
    <el-card class="panel">
      <h3>新增医疗项目</h3>
      <el-form :model="itemForm" label-width="100px" class="item-form">
        <el-form-item label="编码"><el-input v-model="itemForm.code" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="itemForm.name" /></el-form-item>
        <el-form-item label="基础价"><el-input v-model="itemForm.basePrice" /></el-form-item>
        <el-form-item label="资质"><el-input v-model="itemForm.qualification" /></el-form-item>
        <el-form-item label="时段说明"><el-input v-model="itemForm.availabilityNote" /></el-form-item>
        <el-form-item label="异常规则"><el-input v-model="itemForm.exceptionRule" /></el-form-item>
      </el-form>
      <el-button type="primary" :loading="creatingItem" @click="createItem">创建医疗项目</el-button>
    </el-card>

    <AdminTableShell title="医疗预约" :total="total">
      <template #filters>
        <el-input v-model="filters.keyword" placeholder="预约号" clearable />
        <el-select v-model="filters.status" placeholder="状态" clearable>
          <el-option label="已确认" value="CONFIRMED" />
          <el-option label="进行中" value="IN_PROGRESS" />
          <el-option label="已完成" value="COMPLETED" />
          <el-option label="已取消" value="CANCELLED" />
          <el-option label="异常终止" value="EXCEPTION" />
        </el-select>
        <el-button type="primary" @click="search">查询</el-button>
      </template>
      <el-table :data="bookings" data-testid="admin-medical-bookings-table">
        <el-table-column prop="bookingNo" label="预约号" />
        <el-table-column prop="serviceItemName" label="医疗项目" />
        <el-table-column prop="resourceName" label="资源" />
        <el-table-column label="状态"><template slot-scope="{ row }"><StatusTag :status="row.status" :label="statusLabel(row.status)" /></template></el-table-column>
        <el-table-column label="开始时间"><template slot-scope="{ row }">{{ formatTime(row.startAt) }}</template></el-table-column>
        <el-table-column label="履约"><template slot-scope="{ row }">
          <el-button size="mini" :disabled="row.status !== 'CONFIRMED'" @click="transition(row, 'IN_PROGRESS', '开始医疗服务')">开始</el-button>
          <el-button size="mini" type="success" :disabled="row.status !== 'IN_PROGRESS'" @click="transition(row, 'COMPLETED', '完成医疗服务')">完成</el-button>
        </template></el-table-column>
      </el-table>
      <template #pagination><el-pagination background layout="prev, pager, next" :current-page="page.page" :page-size="page.size" :total="total" @current-change="changePage" /></template>
    </AdminTableShell>
  </section>
</template>

<script>
import { createServiceItem, listAdminServiceBookings, transitionServiceBooking } from '@/api/service'
import AdminPageHeader from '@/components/ui/AdminPageHeader.vue'
import AdminTableShell from '@/components/ui/AdminTableShell.vue'
import StatusTag from '@/components/ui/StatusTag.vue'

export default {
  name: 'AdminMedicalView',
  components: { AdminPageHeader, AdminTableShell, StatusTag },
  data() {
    return {
      bookings: [],
      total: 0,
      filters: { keyword: undefined, status: undefined },
      page: { page: 1, size: 10 },
      creatingItem: false,
      itemForm: { code: '', name: '', basePrice: 0, qualification: '', availabilityNote: '', exceptionRule: '' }
    }
  },
  created() { this.loadBookings() },
  methods: {
    async loadBookings() {
      try {
        const response = await listAdminServiceBookings({
          kind: 'MEDICAL',
          keyword: this.filters.keyword || undefined,
          status: this.filters.status || undefined,
          page: this.page.page,
          size: this.page.size
        })
        this.bookings = response.data.items || []
        this.total = response.data.total || 0
      } catch (error) {
        this.$message && this.$message.error(error.message)
      }
    },
    search() { this.page.page = 1; this.loadBookings() },
    changePage(page) { this.page.page = page; this.loadBookings() },
    async createItem() {
      if (!this.itemForm.code || !this.itemForm.name) {
        this.$message && this.$message.warning('请填写编码和名称')
        return
      }
      this.creatingItem = true
      try {
        await createServiceItem({ ...this.itemForm, kind: 'MEDICAL', status: 'ACTIVE' })
        this.$message && this.$message.success('医疗项目已创建')
      } catch (error) {
        this.$message && this.$message.error(error.message)
      } finally {
        this.creatingItem = false
      }
    },
    async transition(row, status, note) {
      try {
        const response = await transitionServiceBooking(row.id, { status, note, version: row.version })
        Object.assign(row, response.data)
        this.$message && this.$message.success('医疗预约状态已更新')
      } catch (error) {
        this.$message && this.$message.error(error.message)
      }
    },
    statusLabel(status) {
      const labels = { CREATED: '已创建', CONFIRMED: '已确认', IN_PROGRESS: '进行中', COMPLETED: '已完成', CANCELLED: '已取消', EXCEPTION: '异常终止' }
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
.admin-console-page { display: grid; gap: 20px; }
.item-form { display: grid; grid-template-columns: repeat(2, minmax(260px, 1fr)); gap: 0 16px; }
@media (max-width: 760px) { .item-form { grid-template-columns: 1fr; } }
</style>
