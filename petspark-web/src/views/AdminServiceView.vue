<template>
  <section class="admin-service">
    <h2>服务管理</h2>
    <div class="toolbar">
      <el-input v-model="filters.keyword" placeholder="预约号/项目" clearable />
      <el-select v-model="filters.status" placeholder="状态" clearable>
        <el-option label="已确认" value="CONFIRMED" />
        <el-option label="进行中" value="IN_PROGRESS" />
        <el-option label="已完成" value="COMPLETED" />
        <el-option label="已取消" value="CANCELLED" />
        <el-option label="异常终止" value="EXCEPTION" />
      </el-select>
      <el-button type="primary" @click="loadBookings">查询</el-button>
    </div>

    <el-table :data="bookings" data-testid="admin-service-bookings-table">
      <el-table-column prop="bookingNo" label="预约号" />
      <el-table-column prop="userId" label="用户" />
      <el-table-column label="状态">
        <template slot-scope="{ row }">
          <el-tag :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="unitPrice" label="单价" />
      <el-table-column label="开始时间">
        <template slot-scope="{ row }">{{ formatTime(row.startAt) }}</template>
      </el-table-column>
      <el-table-column label="操作">
        <template slot-scope="{ row }">
          <el-button size="mini" :disabled="row.status !== 'CONFIRMED'" @click="transition(row, 'IN_PROGRESS', '开始服务')">
            开始
          </el-button>
          <el-button size="mini" type="success" :disabled="row.status !== 'IN_PROGRESS'" @click="transition(row, 'COMPLETED', '完成服务')">
            完成
          </el-button>
        </template>
      </el-table-column>
    </el-table>
  </section>
</template>

<script>
import { listAdminServiceBookings, transitionServiceBooking } from '@/api/service'

export default {
  name: 'AdminServiceView',
  data() {
    return {
      bookings: [],
      total: 0,
      filters: { keyword: undefined, status: undefined },
      page: { page: 1, size: 10 }
    }
  },
  created() {
    this.loadBookings()
  },
  methods: {
    async loadBookings() {
      try {
        const response = await listAdminServiceBookings({
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
    async transition(row, status, note) {
      try {
        const response = await transitionServiceBooking(row.id, { status, note, version: row.version })
        Object.assign(row, response.data)
        this.$message && this.$message.success('状态已更新')
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
.admin-service { max-width: 1100px; margin: 24px auto; }
.toolbar { display: flex; gap: 12px; margin-bottom: 16px; }
</style>
