<template>
  <main class="member-page">
    <page-header
      title="我的订单"
      description="清楚查看订单状态、金额和接下来需要完成的动作。"
    />
    <filter-bar
      ><el-select
        v-model="filters.status"
        placeholder="全部状态"
        clearable
        @change="loadOrders"
        ><el-option
          v-for="item in statusOptions"
          :key="item.value"
          :label="item.label"
          :value="item.value" /></el-select
      ><template #actions
        ><el-button @click="loadOrders">更新列表</el-button></template
      ></filter-bar
    >
    <loading-state v-if="loading" text="正在更新订单状态…" /><error-state
      v-else-if="error"
      title="订单暂时没有加载出来"
      :description="error"
      @retry="loadOrders"
    /><empty-state
      v-else-if="!orders.length"
      title="还没有订单"
      description="选好宠物用品后，订单和履约进度会显示在这里。"
      :image="emptyOrderImage"
    />
    <section v-else class="status-grid" data-testid="my-orders-table">
      <order-status-card
        v-for="order in orders"
        :key="order.id"
        :order="order"
        label="商品订单"
        :status-label="statusLabel(order.status)"
        :next-step="nextStep(order.status)"
        action-text="查看详情"
        @action="openDetail"
      />
    </section>
    <el-dialog
      title="订单详情"
      :visible.sync="showDetail"
      width="min(680px, 94vw)"
      append-to-body
      ><div v-if="current" class="order-detail">
        <status-timeline :items="timelineItems" :active="timelineActive" />
        <dl class="detail-list">
          <div>
            <dt>订单号</dt>
            <dd>{{ current.orderNo }}</dd>
          </div>
          <div>
            <dt>总额</dt>
            <dd>￥{{ current.totalAmount }}</dd>
          </div>
          <div>
            <dt>收货人</dt>
            <dd>{{ current.recipientName }} / {{ current.recipientPhone }}</dd>
          </div>
          <div>
            <dt>地址</dt>
            <dd>{{ current.address }}</dd>
          </div>
          <div v-if="current.cancelReason">
            <dt>取消原因</dt>
            <dd>{{ current.cancelReason }}</dd>
          </div>
        </dl>
        <h3>商品明细</h3>
        <el-table :data="current.items" size="small"
          ><el-table-column prop="sku" label="SKU" /><el-table-column
            prop="name"
            label="名称" /><el-table-column
            prop="unitPrice"
            label="单价" /><el-table-column
            prop="quantity"
            label="数量" /><el-table-column prop="lineAmount" label="行金额"
        /></el-table>
        <div v-if="canCancel(current)" class="cancel-area">
          <el-input v-model="cancelReason" placeholder="取消原因" /><el-button
            type="danger"
            :loading="cancelling"
            @click="submitCancel"
            >取消订单</el-button
          >
        </div>
      </div></el-dialog
    >
  </main>
</template>
<script>
import { cancelOrder, listMyOrders, getOrder } from '@/api/orders'
import PageHeader from '@/components/ui/PageHeader.vue'
import FilterBar from '@/components/ui/FilterBar.vue'
import LoadingState from '@/components/ui/LoadingState.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import StatusTimeline from '@/components/ui/StatusTimeline.vue'
import OrderStatusCard from '@/components/order/OrderStatusCard.vue'
import emptyOrderImage from '@/assets/illustrations/empty-order.png'
const LABELS = {
  CREATED: '已创建',
  PROCESSING: '处理中',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
}
export default {
  name: 'MyOrdersView',
  components: {
    PageHeader,
    FilterBar,
    LoadingState,
    EmptyState,
    ErrorState,
    StatusTimeline,
    OrderStatusCard,
  },
  data() {
    return {
      orders: [],
      total: 0,
      loading: false,
      error: '',
      filters: { status: undefined },
      page: { page: 1, size: 10 },
      showDetail: false,
      current: null,
      cancelReason: '',
      cancelling: false,
      emptyOrderImage,
      statusOptions: Object.entries(LABELS).map(([value, label]) => ({
        value,
        label,
      })),
    }
  },
  computed: {
    timelineActive() {
      return this.current?.status === 'CREATED'
        ? 1
        : this.current?.status === 'PROCESSING'
        ? 2
        : 3
    },
    timelineItems() {
      return [
        { title: '提交订单', description: '订单信息已创建' },
        { title: '商家处理', description: '核对库存与收货信息' },
        {
          title: '履约配送',
          description:
            this.current?.status === 'PROCESSING'
              ? '订单正在处理中'
              : '等待订单进入配送阶段',
        },
        { title: '订单完成', description: '确认商品和服务已送达' },
      ]
    },
  },
  created() {
    this.loadOrders()
  },
  methods: {
    async loadOrders() {
      this.loading = true
      this.error = ''
      try {
        const response = await listMyOrders({
          status: this.filters.status || undefined,
          page: this.page.page,
          size: this.page.size,
        })
        this.orders = response.data.items || []
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
    async openDetail(row) {
      try {
        const response = await getOrder(row.id)
        this.current = response.data
        this.cancelReason = ''
        this.showDetail = true
      } catch (error) {
        this.$message && this.$message.error(error.message)
      }
    },
    canCancel(order) {
      return (
        order && (order.status === 'CREATED' || order.status === 'PROCESSING')
      )
    },
    async submitCancel() {
      if (!this.current) return
      if (!this.cancelReason || !this.cancelReason.trim()) {
        this.$message && this.$message.warning('请填写取消原因')
        return
      }
      this.cancelling = true
      try {
        const response = await cancelOrder(this.current.id, {
          reason: this.cancelReason.trim(),
          version: this.current.version,
        })
        this.current = response.data
        this.$message && this.$message.success('订单已取消')
        await this.loadOrders()
      } catch (error) {
        this.$message && this.$message.error(error.message)
      } finally {
        this.cancelling = false
      }
    },
    statusLabel(status) {
      return LABELS[status] || status
    },
    statusTagType(status) {
      if (status === 'COMPLETED') return 'success'
      if (status === 'CANCELLED') return 'info'
      if (status === 'PROCESSING') return 'warning'
      return ''
    },
    nextStep(status) {
      return (
        {
          CREATED: '等待商家确认订单',
          PROCESSING: '留意履约与配送进度',
          COMPLETED: '订单已完成，可继续选购',
          CANCELLED: '订单已经取消',
        }[status] || '查看最新进度'
      )
    },
    formatTime(value) {
      if (!value) return ''
      const date = new Date(value)
      if (Number.isNaN(date.getTime())) return String(value)
      return new Intl.DateTimeFormat('zh-CN', {
        dateStyle: 'medium',
        timeStyle: 'short',
      }).format(date)
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
  width: 210px;
}
.status-grid {
  display: grid;
  gap: 18px;
}
.order-detail {
  display: grid;
  gap: 20px;
}
.detail-list {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  margin: 0;
}
.detail-list div {
  padding: 12px;
  background: var(--ps-color-surface-soft);
  border-radius: var(--ps-radius-sm);
}
.detail-list dt {
  color: var(--ps-color-muted);
  font-size: 12px;
}
.detail-list dd {
  margin: 3px 0 0;
  font-weight: 600;
  overflow-wrap: anywhere;
}
.order-detail h3 {
  margin: 0;
}
.cancel-area {
  display: flex;
  gap: 12px;
  align-items: center;
}
@media (max-width: 640px) {
  .member-page {
    padding: 24px 16px 40px;
  }
  .ps-filter-bar .el-select {
    width: 100%;
  }
  .detail-list {
    grid-template-columns: 1fr;
  }
  .cancel-area {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
