<template>
  <section class="admin-console-page admin-adoptions">
    <AdminPageHeader eyebrow="审核中心" title="领养审核" description="核验领养申请、记录审核意见并跟进交接。" />
    <AdminTableShell title="领养申请" :total="total">
      <template #filters>
      <el-input v-model="filters.keyword" placeholder="申请号/申请人" clearable />
      <el-select v-model="filters.status" placeholder="状态" clearable>
        <el-option label="待审核" value="PENDING" />
        <el-option label="已通过" value="APPROVED" />
        <el-option label="已驳回" value="REJECTED" />
        <el-option label="已撤回" value="WITHDRAWN" />
        <el-option label="已完成" value="COMPLETED" />
        <el-option label="已取消" value="CANCELLED" />
      </el-select>
      <el-button type="primary" @click="search">查询</el-button>
      </template>

    <el-table :data="applications" data-testid="admin-adoptions-table">
      <el-table-column prop="applicationNo" label="申请号" />
      <el-table-column prop="applicantUsername" label="申请人" />
      <el-table-column prop="petName" label="宠物" />
      <el-table-column label="状态">
        <template slot-scope="{ row }">
          <StatusTag :status="row.status" :label="row.statusLabel || statusLabel(row.status)" />
        </template>
      </el-table-column>
      <el-table-column label="申请时间">
        <template slot-scope="{ row }">{{ formatTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作">
        <template slot-scope="{ row }">
          <el-button size="mini" @click="openDetail(row)">详情</el-button>
          <el-button size="mini" type="success" :disabled="row.status !== 'PENDING'" @click="openDecision(row, 'APPROVED')">通过</el-button>
          <el-button size="mini" type="danger" :disabled="row.status !== 'PENDING'" @click="openDecision(row, 'REJECTED')">驳回</el-button>
        </template>
      </el-table-column>
    </el-table>
      <template #pagination><el-pagination background layout="prev, pager, next" :current-page="page.page" :page-size="page.size" :total="total" @current-change="changePage" /></template>
    </AdminTableShell>

    <el-dialog title="审核详情" :visible.sync="showDetail" width="720px" append-to-body>
      <div v-if="current" class="adoption-detail">
        <status-panel
          :status="current.status"
          :status-label="current.statusLabel || statusLabel(current.status)"
          :status-class="current.statusClass || statusClass(current.status)"
          :reason="current.reason || current.rejectReason || current.withdrawReason || ''"
          :role="current.role || ''"
          :next-step="current.nextStep || ''"
          test-id="admin-adoption-status-panel"
        />
        <p><strong>申请号：</strong>{{ current.applicationNo }}</p>
        <p><strong>申请人：</strong>{{ current.applicantUsername }}</p>
        <p><strong>宠物：</strong>{{ current.pet ? current.pet.name : current.petName }}</p>
        <p><strong>申请时间：</strong>{{ formatTime(current.createdAt) }}</p>
        <p v-if="current.statement"><strong>申请说明：</strong>{{ current.statement }}</p>

        <div v-if="current.status === 'PENDING'" class="decision-area">
          <el-input v-model="decisionNote" placeholder="审核意见" />
          <el-button type="success" :loading="deciding" data-testid="approve-adoption" @click="submitDecision('APPROVED')">通过</el-button>
          <el-button type="danger" :loading="deciding" data-testid="reject-adoption" @click="submitDecision('REJECTED')">驳回</el-button>
        </div>

        <div v-if="current.status === 'APPROVED'" class="handover-area">
          <h4>交接登记</h4>
          <el-select v-model="handoverResult" placeholder="交接结果" data-testid="handover-result">
            <el-option label="交接成功" value="SUCCESS" />
            <el-option label="交接失败" value="FAILURE" />
          </el-select>
          <el-input v-model="handoverNote" placeholder="交接说明（可选）" />
          <el-button type="primary" :loading="handingOver" data-testid="submit-handover" @click="submitHandover">提交交接</el-button>
        </div>
      </div>
    </el-dialog>

    <ConfirmActionDialog
      :visible.sync="decisionDialogVisible"
      :title="pendingDecision === 'APPROVED' ? '通过领养申请' : '驳回领养申请'"
      :description="pendingDecision === 'APPROVED' ? '确认该申请符合领养条件吗？' : '确认驳回该领养申请吗？'"
      :warning="pendingDecision === 'REJECTED' ? '驳回后需由申请人重新提交申请。' : ''"
      :confirm-type="pendingDecision === 'APPROVED' ? 'primary' : 'danger'"
      :confirm-text="pendingDecision === 'APPROVED' ? '确认通过' : '确认驳回'"
      :require-reason="pendingDecision === 'REJECTED'"
      reason-placeholder="请填写审核意见"
      :loading="deciding"
      @confirm="confirmDecision"
    />
  </section>
</template>

<script>
import StatusPanel from '@/components/StatusPanel.vue'
import { listAdminAdoptions, getAdoption, decideAdoption, handoverAdoption } from '@/api/adoption'
import AdminPageHeader from '@/components/ui/AdminPageHeader.vue'
import AdminTableShell from '@/components/ui/AdminTableShell.vue'
import ConfirmActionDialog from '@/components/ui/ConfirmActionDialog.vue'
import StatusTag from '@/components/ui/StatusTag.vue'

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
  name: 'AdminAdoptionsView',
  components: { StatusPanel, AdminPageHeader, AdminTableShell, ConfirmActionDialog, StatusTag },
  data() {
    return {
      applications: [],
      total: 0,
      filters: { keyword: undefined, status: undefined },
      page: { page: 1, size: 10 },
      showDetail: false,
      current: null,
      decisionNote: '',
      deciding: false,
      handoverResult: 'SUCCESS',
      handoverNote: '',
      handingOver: false,
      decisionDialogVisible: false,
      pendingDecision: ''
    }
  },
  created() {
    this.loadAdoptions()
  },
  methods: {
    async loadAdoptions() {
      try {
        const response = await listAdminAdoptions({
          keyword: this.filters.keyword || undefined,
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
    search() { this.page.page = 1; this.loadAdoptions() },
    changePage(page) { this.page.page = page; this.loadAdoptions() },
    async openDetail(row) {
      try {
        const response = await getAdoption(row.id)
        this.current = response.data
        this.decisionNote = ''
        this.handoverResult = 'SUCCESS'
        this.handoverNote = ''
        this.showDetail = true
      } catch (error) {
        this.$message && this.$message.error(error.message)
      }
    },
    async openDecision(row, decision) {
      await this.openDetail(row)
      this.decisionNote = ''
      this.pendingDecision = decision
      this.decisionDialogVisible = true
    },
    async confirmDecision(note) {
      this.decisionNote = note || ''
      await this.submitDecision(this.pendingDecision)
      this.decisionDialogVisible = false
    },
    async submitDecision(decision) {
      if (!this.current) return
      if (decision === 'REJECTED' && (!this.decisionNote || !this.decisionNote.trim())) {
        this.$message && this.$message.warning('驳回请填写审核意见')
        return
      }
      this.deciding = true
      try {
        const response = await decideAdoption(this.current.id, {
          decision,
          note: this.decisionNote ? this.decisionNote.trim() : undefined,
          version: this.current.version
        })
        this.current = response.data
        this.$message && this.$message.success(decision === 'APPROVED' ? '已通过' : '已驳回')
        await this.loadAdoptions()
      } catch (error) {
        this.$message && this.$message.error(error.message)
      } finally {
        this.deciding = false
      }
    },
    async submitHandover() {
      if (!this.current) return
      if (!this.handoverResult) {
        this.$message && this.$message.warning('请选择交接结果')
        return
      }
      if (this.handoverResult === 'FAILURE' && (!this.handoverNote || !this.handoverNote.trim())) {
        this.$message && this.$message.warning('失败请填写说明')
        return
      }
      this.handingOver = true
      try {
        const response = await handoverAdoption(this.current.id, {
          result: this.handoverResult,
          note: this.handoverNote ? this.handoverNote.trim() : undefined,
          version: this.current.version
        })
        this.current = response.data
        this.$message && this.$message.success('交接已登记')
        await this.loadAdoptions()
      } catch (error) {
        this.$message && this.$message.error(error.message)
      } finally {
        this.handingOver = false
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
.admin-console-page { display: grid; gap: 20px; }
.adoption-detail p { margin: 6px 0; }
.decision-area { margin-top: 16px; display: flex; gap: 12px; align-items: center; flex-wrap: wrap; }
.handover-area { margin-top: 20px; display: flex; flex-direction: column; gap: 12px; }
</style>
