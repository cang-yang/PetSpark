<template>
  <main class="member-page">
    <page-header
      title="我的寄养预约"
      description="查看房间确认、照护进度和接回安排。"
      ><template #actions
        ><el-button
          type="primary"
          @click="$router.push({ name: 'boarding-new' })"
          >发起寄养</el-button
        ></template
      ></page-header
    >
    <filter-bar
      ><el-select
        v-model="filters.status"
        placeholder="全部状态"
        clearable
        @change="loadBookings"
        ><el-option
          v-for="item in statusOptions"
          :key="item.value"
          :label="item.label"
          :value="item.value" /></el-select
      ><template #actions
        ><el-button @click="loadBookings">更新列表</el-button></template
      ></filter-bar
    >
    <loading-state v-if="loading" text="正在更新寄养进度…" />
    <error-state
      v-else-if="error"
      title="寄养预约暂时没有加载出来"
      :description="error"
      @retry="loadBookings"
    />
    <empty-state
      v-else-if="!bookings.length"
      title="还没有寄养预约"
      description="需要临时托付小伙伴时，可以提前选择日期并说明照护习惯。"
      action-text="发起寄养"
      :image="emptyOrderImage"
      @action="$router.push({ name: 'boarding-new' })"
    />
    <section v-else class="status-grid" data-testid="my-boarding-table">
      <order-status-card
        v-for="booking in bookings"
        :key="booking.id"
        :order="booking"
        label="寄养预约"
        :status-label="statusLabel(booking.status)"
        :next-step="nextStep(booking.status)"
        :secondary-action-text="canCancel(booking) ? '取消预约' : ''"
        @secondary-action="cancel"
        ><template #default
          ><p v-if="booking.roomName" class="room-name">
            照护房间 · {{ booking.roomName }}
          </p></template
        ></order-status-card
      >
    </section>
    <el-dialog
      title="取消预约"
      :visible.sync="showCancel"
      width="min(480px, 92vw)"
      ><el-form label-position="top"
        ><el-form-item label="取消原因"
          ><el-input
            v-model="cancelReason"
            type="textarea"
            :rows="3"
            maxlength="255"
            show-word-limit /></el-form-item></el-form
      ><span slot="footer"
        ><el-button @click="showCancel = false">返回</el-button
        ><el-button type="danger" :loading="cancelling" @click="submitCancel"
          >确认取消</el-button
        ></span
      ></el-dialog
    >
  </main>
</template>

<script>
import { listMyBookings, cancelBooking } from '@/api/boarding'
import PageHeader from '@/components/ui/PageHeader.vue'
import FilterBar from '@/components/ui/FilterBar.vue'
import LoadingState from '@/components/ui/LoadingState.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import OrderStatusCard from '@/components/order/OrderStatusCard.vue'
import emptyOrderImage from '@/assets/illustrations/empty-order.png'

const LABELS = {
  PENDING_CONFIRMATION: '待确认',
  CONFIRMED: '已确认',
  IN_SERVICE: '履约中',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
  REJECTED: '已拒绝',
  TERMINATED: '已终止',
}
export default {
  name: 'MyBoardingsView',
  components: {
    PageHeader,
    FilterBar,
    LoadingState,
    EmptyState,
    ErrorState,
    OrderStatusCard,
  },
  data() {
    return {
      bookings: [],
      total: 0,
      loading: false,
      error: '',
      filters: { status: undefined },
      page: { page: 1, size: 10 },
      showCancel: false,
      cancelTarget: null,
      cancelReason: '',
      cancelling: false,
      emptyOrderImage,
      statusOptions: Object.entries(LABELS).map(([value, label]) => ({
        value,
        label,
      })),
    }
  },
  created() {
    this.loadBookings()
  },
  methods: {
    async loadBookings() {
      this.loading = true
      this.error = ''
      try {
        const response = await listMyBookings({
          status: this.filters.status || undefined,
          page: this.page.page,
          size: this.page.size,
        })
        this.bookings = response.data.items || []
        this.total = response.data.total || 0
      } catch (error) {
        this.error =
          error.response?.data?.message ||
          error.message ||
          '请检查网络连接后重试。'
      } finally {
        this.loading = false
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
          version: this.cancelTarget.version,
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
      return (
        row &&
        ['PENDING_CONFIRMATION', 'CONFIRMED', 'IN_SERVICE'].includes(row.status)
      )
    },
    statusLabel(status) {
      return LABELS[status] || status
    },
    statusTagType(status) {
      if (status === 'COMPLETED') return 'success'
      if (['CANCELLED', 'REJECTED', 'TERMINATED'].includes(status))
        return 'info'
      if (status === 'IN_SERVICE') return 'warning'
      return ''
    },
    nextStep(status) {
      return (
        {
          PENDING_CONFIRMATION: '等待寄养团队确认房间',
          CONFIRMED: '按约定日期办理入住',
          IN_SERVICE: '关注照护进度并准备接回',
          COMPLETED: '寄养服务已完成',
          CANCELLED: '预约已经取消',
          REJECTED: '查看原因后调整日期',
          TERMINATED: '联系平台了解终止原因',
        }[status] || '查看最新安排'
      )
    },
  },
}
</script>

<style scoped>
.member-page {
  width: min(100%, 960px);
  margin: 0 auto;
  padding: 36px 24px 56px;
}
.ps-filter-bar .el-select {
  width: 220px;
}
.status-grid {
  display: grid;
  gap: 18px;
}
.room-name {
  margin: 14px 0 0;
  color: var(--ps-color-muted);
  font-size: 13px;
}
@media (max-width: 640px) {
  .member-page {
    padding: 24px 16px 40px;
  }
  .ps-filter-bar .el-select {
    width: 100%;
  }
}
</style>
