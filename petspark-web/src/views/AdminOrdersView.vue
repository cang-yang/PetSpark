<template>
  <section class="admin-console-page admin-orders">
    <AdminPageHeader eyebrow="交易履约" title="订单管理" description="跟进订单处理、完成与取消状态。" />
    <AdminTableShell title="订单列表" :total="total">
      <template #filters>
      <el-input v-model="filters.keyword" placeholder="订单号" clearable />
      <el-select v-model="filters.status" placeholder="状态" clearable>
        <el-option label="已创建" value="CREATED" />
        <el-option label="处理中" value="PROCESSING" />
        <el-option label="已完成" value="COMPLETED" />
        <el-option label="已取消" value="CANCELLED" />
      </el-select>
      <el-button type="primary" @click="search">查询</el-button>
      </template>

    <el-table :data="orders" data-testid="admin-orders-table">
      <el-table-column prop="orderNo" label="订单号" />
      <el-table-column prop="userId" label="用户" />
      <el-table-column label="状态">
        <template slot-scope="{ row }">
          <StatusTag :status="row.status" :label="statusLabel(row.status)" />
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
          <el-button size="mini" type="danger" :disabled="!canCancel(row)" @click="requestCancel(row)">
            取消
          </el-button>
        </template>
      </el-table-column>
    </el-table>
      <template #pagination>
        <el-pagination
          background
          layout="prev, pager, next"
          :current-page="page.page"
          :page-size="page.size"
          :total="total"
          @current-change="changePage"
        />
      </template>
    </AdminTableShell>

    <ConfirmActionDialog
      :visible.sync="cancelDialogVisible"
      title="取消订单"
      description="确认取消当前订单吗？"
      warning="取消后订单将无法继续履约。"
      confirm-text="确认取消"
      require-reason
      reason-placeholder="请填写取消原因"
      :loading="cancelling"
      @confirm="confirmCancel"
    />
  </section>
</template>

<script>
import { cancelOrder, listAdminOrders, transitionOrder } from '@/api/orders'
import AdminPageHeader from '@/components/ui/AdminPageHeader.vue'
import AdminTableShell from '@/components/ui/AdminTableShell.vue'
import ConfirmActionDialog from '@/components/ui/ConfirmActionDialog.vue'
import StatusTag from '@/components/ui/StatusTag.vue'

export default {
  name: 'AdminOrdersView',
  components: { AdminPageHeader, AdminTableShell, ConfirmActionDialog, StatusTag },
  data() {
    return {
      orders: [],
      total: 0,
      filters: { keyword: undefined, status: undefined },
      page: { page: 1, size: 10 },
      cancelDialogVisible: false,
      cancelling: false,
      pendingCancelOrder: null
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
    search() {
      this.page.page = 1
      this.loadOrders()
    },
    changePage(page) {
      this.page.page = page
      this.loadOrders()
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
    requestCancel(row) {
      this.pendingCancelOrder = row
      this.cancelDialogVisible = true
    },
    cancel(row) {
      this.requestCancel(row)
    },
    async confirmCancel(reason) {
      const row = this.pendingCancelOrder
      if (!row || !reason) return
      this.cancelling = true
      try {
        const response = await cancelOrder(row.id, { reason, version: row.version })
        Object.assign(row, response.data)
        this.cancelDialogVisible = false
        this.pendingCancelOrder = null
        this.$message && this.$message.success('订单已取消')
      } catch (error) {
        this.$message && this.$message.error(error.message)
      } finally {
        this.cancelling = false
      }
    },
    canCancel(row) {
      return row && (row.status === 'CREATED' || row.status === 'PROCESSING')
    },
    statusLabel(status) {
      const labels = { CREATED: '已创建', PROCESSING: '处理中', COMPLETED: '已完成', CANCELLED: '已取消' }
      return labels[status] || status
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
</style>
