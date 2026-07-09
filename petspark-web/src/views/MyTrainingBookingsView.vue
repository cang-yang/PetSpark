<template>
  <section class="my-training-bookings">
    <h2>我的训练预约</h2>
    <div class="toolbar">
      <el-select v-model="filters.status" placeholder="状态" clearable @change="loadBookings">
        <el-option label="已确认" value="CONFIRMED" />
        <el-option label="训练中" value="IN_PROGRESS" />
        <el-option label="已完成" value="COMPLETED" />
        <el-option label="已取消" value="CANCELLED" />
        <el-option label="异常终止" value="EXCEPTION" />
      </el-select>
      <el-button type="primary" @click="loadBookings">查询</el-button>
    </div>

    <el-table :data="bookings" data-testid="my-training-bookings-table">
      <el-table-column prop="bookingNo" label="预约号" />
      <el-table-column prop="serviceItemName" label="训练项目" />
      <el-table-column label="状态">
        <template slot-scope="{ row }">
          <el-tag :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="开始时间">
        <template slot-scope="{ row }">{{ formatTime(row.startAt) }}</template>
      </el-table-column>
      <el-table-column label="操作">
        <template slot-scope="{ row }">
          <el-button size="mini" @click="openDetail(row)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog title="训练预约详情" :visible.sync="showDetail" width="680px">
      <div v-if="current" class="booking-detail">
        <p><strong>预约号：</strong>{{ current.bookingNo }}</p>
        <p><strong>状态：</strong>{{ statusLabel(current.status) }}</p>
        <p><strong>训练项目：</strong>{{ current.serviceItemName }}</p>
        <p><strong>联系人：</strong>{{ current.customerName }} / {{ current.customerPhone }}</p>
        <p><strong>时间：</strong>{{ formatTime(current.startAt) }} ~ {{ formatTime(current.endAt) }}</p>
        <template v-if="current.trainingDetail">
          <p><strong>训练目标：</strong>{{ current.trainingDetail.trainingGoal }}</p>
          <p v-if="current.trainingDetail.behaviorProblem"><strong>行为问题：</strong>{{ current.trainingDetail.behaviorProblem }}</p>
          <p><strong>训练强度：</strong>{{ intensityLabel(current.trainingDetail.intensity) }}</p>
          <p v-if="current.trainingDetail.attentionNote"><strong>注意事项：</strong>{{ current.trainingDetail.attentionNote }}</p>
        </template>
        <p v-if="current.remark"><strong>备注：</strong>{{ current.remark }}</p>
        <p v-if="current.cancelReason"><strong>取消原因：</strong>{{ current.cancelReason }}</p>
        <p v-if="current.exceptionNote"><strong>异常说明：</strong>{{ current.exceptionNote }}</p>
        <div v-if="canCancel(current)" class="cancel-area">
          <el-input v-model="cancelReason" placeholder="取消原因" />
          <el-button type="danger" :loading="cancelling" @click="submitCancel">取消预约</el-button>
        </div>
        <div v-if="canException(current)" class="cancel-area">
          <el-input v-model="exceptionReason" placeholder="异常说明" />
          <el-button type="warning" :loading="exceptioning" @click="submitException">异常终止</el-button>
        </div>
      </div>
    </el-dialog>
  </section>
</template>

<script>
import { listMyTrainingBookings, getTrainingBooking, cancelTrainingBooking, exceptionTrainingBooking } from '@/api/training'

export default {
  name: 'MyTrainingBookingsView',
  data() {
    return {
      bookings: [],
      total: 0,
      filters: { status: undefined },
      page: { page: 1, size: 10 },
      showDetail: false,
      current: null,
      cancelReason: '',
      cancelling: false,
      exceptionReason: '',
      exceptioning: false
    }
  },
  created() {
    this.loadBookings()
  },
  methods: {
    async loadBookings() {
      try {
        const response = await listMyTrainingBookings({
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
        const response = await getTrainingBooking(row.id)
        this.current = response.data
        this.cancelReason = ''
        this.exceptionReason = ''
        this.showDetail = true
      } catch (error) {
        this.$message && this.$message.error(error.message)
      }
    },
    canCancel(booking) {
      return booking && (booking.status === 'CONFIRMED' || booking.status === 'IN_PROGRESS')
    },
    canException(booking) {
      return booking && (booking.status === 'CONFIRMED' || booking.status === 'IN_PROGRESS')
    },
    async submitCancel() {
      if (!this.current) return
      if (!this.cancelReason || !this.cancelReason.trim()) {
        this.$message && this.$message.warning('请填写取消原因')
        return
      }
      this.cancelling = true
      try {
        const response = await cancelTrainingBooking(this.current.id, {
          reason: this.cancelReason.trim(),
          version: this.current.version
        })
        this.current = response.data
        this.$message && this.$message.success('训练预约已取消')
        await this.loadBookings()
      } catch (error) {
        this.$message && this.$message.error(error.message)
      } finally {
        this.cancelling = false
      }
    },
    async submitException() {
      if (!this.current) return
      if (!this.exceptionReason || !this.exceptionReason.trim()) {
        this.$message && this.$message.warning('请填写异常说明')
        return
      }
      this.exceptioning = true
      try {
        const response = await exceptionTrainingBooking(this.current.id, {
          note: this.exceptionReason.trim(),
          version: this.current.version
        })
        this.current = response.data
        this.$message && this.$message.success('已异常终止')
        await this.loadBookings()
      } catch (error) {
        this.$message && this.$message.error(error.message)
      } finally {
        this.exceptioning = false
      }
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
    intensityLabel(intensity) {
      const labels = { LOW: '低强度', MEDIUM: '中等强度', HIGH: '高强度' }
      return labels[intensity] || intensity
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
.my-training-bookings { max-width: 960px; margin: 24px auto; }
.toolbar { display: flex; gap: 12px; margin-bottom: 16px; }
.booking-detail p { margin: 6px 0; }
.cancel-area { margin-top: 16px; display: flex; gap: 12px; align-items: center; }
</style>
