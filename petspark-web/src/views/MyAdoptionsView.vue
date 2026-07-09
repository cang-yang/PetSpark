<template>
  <section class="my-adoptions">
    <h2>我的领养申请</h2>
    <div class="toolbar">
      <el-select v-model="filters.status" placeholder="状态" clearable @change="loadAdoptions">
        <el-option label="待审核" value="PENDING" />
        <el-option label="已通过" value="APPROVED" />
        <el-option label="已驳回" value="REJECTED" />
        <el-option label="已撤回" value="WITHDRAWN" />
        <el-option label="已完成" value="COMPLETED" />
        <el-option label="已取消" value="CANCELLED" />
      </el-select>
      <el-button type="primary" @click="loadAdoptions">查询</el-button>
    </div>

    <el-table :data="applications" data-testid="my-adoptions-table">
      <el-table-column prop="applicationNo" label="申请号" />
      <el-table-column prop="petName" label="宠物" />
      <el-table-column label="状态">
        <template slot-scope="{ row }">
          <el-tag :type="statusTagType(row.status)">{{ row.statusLabel || statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="申请时间">
        <template slot-scope="{ row }">{{ formatTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作">
        <template slot-scope="{ row }">
          <el-button size="mini" @click="openDetail(row)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog title="领养申请详情" :visible.sync="showDetail" width="640px">
      <div v-if="current" class="adoption-detail">
        <status-panel
          :status="current.status"
          :status-label="current.statusLabel || statusLabel(current.status)"
          :status-class="current.statusClass || statusClass(current.status)"
          :reason="current.reason || current.rejectReason || current.withdrawReason || ''"
          :role="current.role || ''"
          :next-step="current.nextStep || ''"
          test-id="adoption-status-panel"
        />
        <p><strong>申请号：</strong>{{ current.applicationNo }}</p>
        <p><strong>宠物：</strong>{{ current.pet ? current.pet.name : current.petName }}</p>
        <p><strong>申请时间：</strong>{{ formatTime(current.createdAt) }}</p>
        <p v-if="current.statement"><strong>申请说明：</strong>{{ current.statement }}</p>

        <div v-if="canWithdraw(current)" class="withdraw-area">
          <el-input v-model="withdrawReason" placeholder="撤回原因（可选）" />
          <el-button type="danger" :loading="withdrawing" data-testid="withdraw-adoption" @click="submitWithdraw">撤回申请</el-button>
        </div>
      </div>
    </el-dialog>
  </section>
</template>

<script>
import StatusPanel from '@/components/StatusPanel.vue'
import { listMyAdoptions, getAdoption, withdrawAdoption } from '@/api/adoption'

const STATUS_LABELS = {
  PENDING: '待审核',
  APPROVED: '已通过',
  REJECTED: '已驳回',
  WITHDRAWN: '已撤回',
  COMPLETED: '已完成',
  CANCELLED: '已取消'
}
const STATUS_CLASSES = {
  PENDING: 'warning',
  APPROVED: 'info',
  REJECTED: 'error',
  WITHDRAWN: 'info',
  COMPLETED: 'success',
  CANCELLED: 'info'
}

export default {
  name: 'MyAdoptionsView',
  components: { StatusPanel },
  data() {
    return {
      applications: [],
      total: 0,
      filters: { status: undefined },
      page: { page: 1, size: 10 },
      showDetail: false,
      current: null,
      withdrawReason: '',
      withdrawing: false
    }
  },
  created() {
    this.loadAdoptions()
  },
  methods: {
    async loadAdoptions() {
      try {
        const response = await listMyAdoptions({
          status: this.filters.status || undefined,
          page: this.page.page,
          size: this.page.size
        })
        this.applications = response.data.items || []
        this.total = response.data.total || 0
      } catch (error) {
        this.$message && this.$message.error(error.message)
      }
    },
    async openDetail(row) {
      try {
        const response = await getAdoption(row.id)
        this.current = response.data
        this.withdrawReason = ''
        this.showDetail = true
      } catch (error) {
        this.$message && this.$message.error(error.message)
      }
    },
    canWithdraw(application) {
      return application && (application.status === 'PENDING' || application.status === 'APPROVED')
    },
    async submitWithdraw() {
      if (!this.current) return
      this.withdrawing = true
      try {
        const response = await withdrawAdoption(this.current.id, {
          reason: this.withdrawReason ? this.withdrawReason.trim() : undefined,
          version: this.current.version
        })
        this.current = response.data
        this.$message && this.$message.success('申请已撤回')
        await this.loadAdoptions()
      } catch (error) {
        this.$message && this.$message.error(error.message)
      } finally {
        this.withdrawing = false
      }
    },
    statusLabel(status) {
      return STATUS_LABELS[status] || status
    },
    statusClass(status) {
      return STATUS_CLASSES[status] || 'info'
    },
    statusTagType(status) {
      if (status === 'COMPLETED') return 'success'
      if (status === 'REJECTED') return 'danger'
      if (status === 'PENDING') return 'warning'
      if (status === 'APPROVED') return ''
      return 'info'
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
.my-adoptions { max-width: 960px; margin: 24px auto; }
.toolbar { display: flex; gap: 12px; margin-bottom: 16px; }
.adoption-detail p { margin: 6px 0; }
.withdraw-area { margin-top: 16px; display: flex; gap: 12px; align-items: center; }
</style>
