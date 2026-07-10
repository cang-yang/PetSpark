<template>
  <section class="my-beauty-bookings">
    <h2>我的美容预约</h2>
    <div class="toolbar">
      <el-select v-model="filters.status" placeholder="状态" clearable @change="loadBookings">
        <el-option label="已确认" value="CONFIRMED" />
        <el-option label="进行中" value="IN_PROGRESS" />
        <el-option label="已完成" value="COMPLETED" />
        <el-option label="已取消" value="CANCELLED" />
        <el-option label="异常终止" value="EXCEPTION" />
      </el-select>
      <el-button type="primary" @click="loadBookings">查询</el-button>
    </div>
    <el-table :data="bookings" data-testid="my-beauty-bookings-table">
      <el-table-column prop="bookingNo" label="预约号" />
      <el-table-column prop="serviceItemName" label="美容项目" />
      <el-table-column label="状态"><template slot-scope="{ row }"><el-tag :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag></template></el-table-column>
      <el-table-column label="开始时间"><template slot-scope="{ row }">{{ formatTime(row.startAt) }}</template></el-table-column>
      <el-table-column label="操作"><template slot-scope="{ row }"><el-button size="mini" @click="openDetail(row)">详情</el-button></template></el-table-column>
    </el-table>

    <el-dialog title="美容预约详情" :visible.sync="showDetail" width="640px" append-to-body>
      <div v-if="current" class="booking-detail">
        <p><strong>预约号：</strong>{{ current.bookingNo }}</p>
        <p><strong>项目：</strong>{{ current.serviceItemName }}</p>
        <p><strong>状态：</strong>{{ statusLabel(current.status) }}</p>
        <p><strong>联系人：</strong>{{ current.customerName }} / {{ current.customerPhone }}</p>
        <p><strong>时间：</strong>{{ formatTime(current.startAt) }} ~ {{ formatTime(current.endAt) }}</p>
        <p v-if="current.remark"><strong>护理备注：</strong>{{ current.remark }}</p>
        <div v-if="canCancel(current)" class="cancel-area">
          <el-input v-model="cancelReason" placeholder="取消原因" />
          <el-button type="danger" :loading="cancelling" @click="submitCancel">取消预约</el-button>
        </div>
      </div>
    </el-dialog>
  </section>
</template>

<script>
import { listMyServiceBookings, getServiceBooking, cancelServiceBooking } from '@/api/service'

export default {
  name: 'MyBeautyBookingsView',
  data() {
    return {
      bookings: [],
      total: 0,
      filters: { status: undefined },
      page: { page: 1, size: 10 },
      showDetail: false,
      current: null,
      cancelReason: '',
      cancelling: false
    }
  },
  created() {
    this.loadBookings()
  },
  methods: {
    async loadBookings() {
      try {
        const response = await listMyServiceBookings({
          kind: 'BEAUTY',
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
    async openDetail(row) {
      try {
        const response = await getServiceBooking(row.id)
        this.current = response.data
        this.cancelReason = ''
        this.showDetail = true
      } catch (error) {
        this.$message && this.$message.error(error.message)
      }
    },
    canCancel(booking) { return booking && (booking.status === 'CONFIRMED' || booking.status === 'IN_PROGRESS') },
    async submitCancel() {
      if (!this.current || !this.cancelReason.trim()) return
      this.cancelling = true
      try {
        const response = await cancelServiceBooking(this.current.id, { reason: this.cancelReason.trim(), version: this.current.version })
        this.current = response.data
        this.$message && this.$message.success('美容预约已取消')
        await this.loadBookings()
      } catch (error) {
        this.$message && this.$message.error(error.message)
      } finally {
        this.cancelling = false
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
.my-beauty-bookings { max-width: 960px; margin: 24px auto; }
.toolbar { display: flex; gap: 12px; margin-bottom: 16px; }
.booking-detail p { margin: 6px 0; }
.cancel-area { margin-top: 16px; display: flex; gap: 12px; align-items: center; }
</style>
