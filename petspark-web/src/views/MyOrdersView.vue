<template>
  <section class="my-orders">
    <h2>我的订单</h2>
    <div class="toolbar">
      <el-select v-model="filters.status" placeholder="状态" clearable @change="loadOrders">
        <el-option label="已创建" value="CREATED" />
        <el-option label="处理中" value="PROCESSING" />
        <el-option label="已完成" value="COMPLETED" />
        <el-option label="已取消" value="CANCELLED" />
      </el-select>
      <el-button type="primary" @click="loadOrders">查询</el-button>
    </div>

    <el-table :data="orders" data-testid="my-orders-table">
      <el-table-column prop="orderNo" label="订单号" />
      <el-table-column label="状态">
        <template slot-scope="{ row }">
          <el-tag :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="totalAmount" label="总额" />
      <el-table-column label="创建时间">
        <template slot-scope="{ row }">{{ formatTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作">
        <template slot-scope="{ row }">
          <el-button size="mini" @click="openDetail(row)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog title="订单详情" :visible.sync="showDetail" width="640px">
      <div v-if="current" class="order-detail">
        <p><strong>订单号：</strong>{{ current.orderNo }}</p>
        <p><strong>状态：</strong>{{ statusLabel(current.status) }}</p>
        <p><strong>总额：</strong>￥{{ current.totalAmount }}</p>
        <p><strong>收货人：</strong>{{ current.recipientName }} / {{ current.recipientPhone }}</p>
        <p><strong>地址：</strong>{{ current.address }}</p>
        <p v-if="current.cancelReason"><strong>取消原因：</strong>{{ current.cancelReason }}</p>
        <h4>商品明细</h4>
        <el-table :data="current.items" size="small">
          <el-table-column prop="sku" label="SKU" />
          <el-table-column prop="name" label="名称" />
          <el-table-column prop="unitPrice" label="单价" />
          <el-table-column prop="quantity" label="数量" />
          <el-table-column prop="lineAmount" label="行金额" />
        </el-table>
        <div v-if="canCancel(current)" class="cancel-area">
          <el-input v-model="cancelReason" placeholder="取消原因" />
          <el-button type="danger" :loading="cancelling" @click="submitCancel">取消订单</el-button>
        </div>
      </div>
    </el-dialog>
  </section>
</template>

<script>
import { cancelOrder, listMyOrders, getOrder } from '@/api/orders'

export default {
  name: 'MyOrdersView',
  data() {
    return {
      orders: [],
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
    this.loadOrders()
  },
  methods: {
    async loadOrders() {
      try {
        const response = await listMyOrders({
          status: this.filters.status || undefined,
          page: this.page.page,
          size: this.page.size
        })
        this.orders = response.data.items || []
        this.total = response.data.total || 0
      } catch (error) {
        this.$message && this.$message.error(error.message)
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
      return order && (order.status === 'CREATED' || order.status === 'PROCESSING')
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
          version: this.current.version
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
      const labels = { CREATED: '已创建', PROCESSING: '处理中', COMPLETED: '已完成', CANCELLED: '已取消' }
      return labels[status] || status
    },
    statusTagType(status) {
      if (status === 'COMPLETED') return 'success'
      if (status === 'CANCELLED') return 'info'
      if (status === 'PROCESSING') return 'warning'
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
.my-orders { max-width: 960px; margin: 24px auto; }
.toolbar { display: flex; gap: 12px; margin-bottom: 16px; }
.order-detail p { margin: 6px 0; }
.cancel-area { margin-top: 16px; display: flex; gap: 12px; align-items: center; }
</style>
