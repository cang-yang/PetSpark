<template>
  <section class="my-boardings">
    <h2 data-testid="my-boarding-title">我的寄养预约</h2>
    <div class="toolbar">
      <el-select v-model="filters.status" placeholder="状态" clearable @change="loadBookings">
        <el-option label="待确认" value="PENDING_CONFIRMATION" />
        <el-option label="已确认" value="CONFIRMED" />
        <el-option label="履约中" value="IN_SERVICE" />
        <el-option label="已完成" value="COMPLETED" />
        <el-option label="已取消" value="CANCELLED" />
        <el-option label="已拒绝" value="REJECTED" />
        <el-option label="已终止" value="TERMINATED" />
      </el-select>
      <el-button type="primary" @click="loadBookings">查询</el-button>
    </div>

    <el-table :data="bookings" data-testid="my-boarding-table">
      <el-table-column prop="bookingNo" label="预约号" />
      <el-table-column prop="petName" label="宠物" />
      <el-table-column prop="roomName" label="房间" />
      <el-table-column label="日期">
        <template slot-scope="{ row }">{{ row.startDate }} ~ {{ row.endDate }}</template>
      </el-table-column>
      <el-table-column label="状态">
        <template slot-scope="{ row }">
          <el-tag :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作">
        <template slot-scope="{ row }">
          <el-button size="mini" type="danger" :disabled="!canCancel(row)" @click="cancel(row)">取消</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog title="取消预约" :visible.sync="showCancel" width="480px">
      <el-form label-width="80px">
        <el-form-item label="原因">
          <el-input v-model="cancelReason" type="textarea" :rows="3" maxlength="255" show-word-limit />
        </el-form-item>
      </el-form>
      <span slot="footer">
        <el-button @click="showCancel = false">取消</el-button>
        <el-button type="danger" :loading="cancelling" @click="submitCancel">确认取消</el-button>
      </span>
    </el-dialog>
  </section>
</template>

<script>
import { listMyBookings, cancelBooking } from '@/api/boarding'

export default {
  name: 'MyBoardingsView',
  data() {
    return {
      bookings: [],
      total: 0,
      filters: { status: undefined },
      page: { page: 1, size: 10 },
      showCancel: false,
      cancelTarget: null,
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
        const response = await listMyBookings({
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
    cancel(row) {
      this.cancelTarget = row
      this.cancelReason = ''
      this.showCancel = true
    },
    async submitCancel() {
      if (!this.cancelTarget) return
      if (!this.cancelReason || !this.cancelReason.trim()) {
        this.$message && this.$message.warning('请填写取消原因')
        return
      }
      this.cancelling = true
      try {
        await cancelBooking(this.cancelTarget.id, {
          reason: this.cancelReason.trim(),
          version: this.cancelTarget.version
        })
        this.$message && this.$message.success('预约已取消')
        this.showCancel = false
        await this.loadBookings()
      } catch (error) {
        this.$message && this.$message.error(error.message)
      } finally {
        this.cancelling = false
      }
    },
    canCancel(row) {
      return row && (row.status === 'PENDING_CONFIRMATION' || row.status === 'CONFIRMED' || row.status === 'IN_SERVICE')
    },
    statusLabel(status) {
      const labels = {
        PENDING_CONFIRMATION: '待确认', CONFIRMED: '已确认', IN_SERVICE: '履约中',
        COMPLETED: '已完成', CANCELLED: '已取消', REJECTED: '已拒绝', TERMINATED: '已终止'
      }
      return labels[status] || status
    },
    statusTagType(status) {
      if (status === 'COMPLETED') return 'success'
      if (status === 'CANCELLED' || status === 'REJECTED' || status === 'TERMINATED') return 'info'
      if (status === 'IN_SERVICE') return 'warning'
      return ''
    }
  }
}
</script>

<style scoped>
.my-boardings { max-width: 960px; margin: 24px auto; }
.toolbar { display: flex; gap: 12px; margin-bottom: 16px; }
</style>
