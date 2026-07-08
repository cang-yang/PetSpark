<template>
  <section class="admin-orders">
    <h2>订单管理</h2>
    <div class="toolbar">
      <el-input v-model="filters.keyword" placeholder="订单号" clearable />
      <el-select v-model="filters.status" placeholder="状态" clearable>
        <el-option label="已创建" value="CREATED" />
        <el-option label="处理中" value="PROCESSING" />
        <el-option label="已完成" value="COMPLETED" />
        <el-option label="已取消" value="CANCELLED" />
      </el-select>
      <el-button type="primary" @click="loadOrders">查询</el-button>
    </div>

    <el-table :data="orders" data-testid="admin-orders-table">
      <el-table-column prop="orderNo" label="订单号" />
      <el-table-column prop="userId" label="用户" />
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
          <el-button size="mini" :disabled="row.status !== 'CREATED'" @click="transition(row, 'PROCESSING', '开始处理')">
            处理
          </el-button>
          <el-button size="mini" type="success" :disabled="row.status !== 'PROCESSING'" @click="transition(row, 'COMPLETED', '完成')">
            完成
          </el-button>
          <el-button size="mini" type="danger" :disabled="!canCancel(row)" @click="cancel(row)">
            取消
          </el-button>
        </template>
      </el-table-column>
    </el-table>
  </section>
</template>

<script>
import { cancelOrder, listAdminOrders, transitionOrder } from '@/api/orders'

export default {
  name: 'AdminOrdersView',
  data() {
    return {
      orders: [],
      total: 0,
      filters: { keyword: undefined, status: undefined },
      page: { page: 1, size: 10 }
    }
  },
  created() {
    this.loadOrders()
  },
  methods: {
    async loadOrders() {
      try {
        const response = await listAdminOrders({
          keyword: this.filters.keyword || undefined,
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
    async transition(row, status, note) {
      try {
        const response = await transitionOrder(row.id, { status, note, version: row.version })
        Object.assign(row, response.data)
        this.$message && this.$message.success('状态已更新')
      } catch (error) {
        this.$message && this.$message.error(error.message)
      }
    },
    async cancel(row) {
      const reason = window.prompt('取消原因')
      if (!reason) return
      try {
        const response = await cancelOrder(row.id, { reason, version: row.version })
        Object.assign(row, response.data)
        this.$message && this.$message.success('订单已取消')
      } catch (error) {
        this.$message && this.$message.error(error.message)
      }
    },
    canCancel(row) {
      return row && (row.status === 'CREATED' || row.status === 'PROCESSING')
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
.admin-orders { max-width: 1100px; margin: 24px auto; }
.toolbar { display: flex; gap: 12px; margin-bottom: 16px; }
</style>
