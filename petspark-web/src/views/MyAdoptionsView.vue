<template>
  <main class="member-page">
    <page-header
      title="我的领养申请"
      description="查看审核进展、交接安排和每一步需要完成的事项。"
    />
    <filter-bar>
      <el-select
        v-model="filters.status"
        placeholder="全部状态"
        clearable
        @change="loadAdoptions"
      >
        <el-option
          v-for="item in statusOptions"
          :key="item.value"
          :label="item.label"
          :value="item.value"
        />
      </el-select>
      <template #actions
        ><el-button type="primary" @click="loadAdoptions"
          >更新列表</el-button
        ></template
      >
    </filter-bar>

    <loading-state v-if="loading" text="正在更新领养进度…" />
    <error-state
      v-else-if="error"
      title="领养申请暂时没有加载出来"
      :description="error"
      @retry="loadAdoptions"
    />
    <empty-state
      v-else-if="!applications.length"
      title="还没有领养申请"
      description="遇到愿意长期陪伴的伙伴后，可以从领养页提交申请。"
      :image="emptyPetImage"
    />
    <section v-else class="status-grid" data-testid="my-adoptions-table">
      <order-status-card
        v-for="application in applications"
        :key="application.id"
        :order="application"
        label="领养申请"
        :status-label="
          application.statusLabel || statusLabel(application.status)
        "
        :next-step="application.nextStep || nextStep(application.status)"
        action-text="查看申请"
        @action="openDetail"
      />
    </section>

    <el-dialog
      title="领养申请详情"
      :visible.sync="showDetail"
      width="min(640px, 92vw)"
    >
      <div v-if="current" class="adoption-detail">
        <status-panel
          :status="current.status"
          :status-label="current.statusLabel || statusLabel(current.status)"
          :status-class="current.statusClass || statusClass(current.status)"
          :reason="
            current.reason ||
            current.rejectReason ||
            current.withdrawReason ||
            ''
          "
          :role="current.role || ''"
          :next-step="current.nextStep || nextStep(current.status)"
          test-id="adoption-status-panel"
        />
        <status-timeline :items="timelineItems" :active="timelineActive" />
        <dl class="detail-list">
          <div>
            <dt>申请号</dt>
            <dd>{{ current.applicationNo }}</dd>
          </div>
          <div>
            <dt>宠物</dt>
            <dd>{{ current.pet ? current.pet.name : current.petName }}</dd>
          </div>
          <div>
            <dt>申请时间</dt>
            <dd>{{ formatTime(current.createdAt) }}</dd>
          </div>
          <div v-if="current.statement">
            <dt>申请说明</dt>
            <dd>{{ current.statement }}</dd>
          </div>
        </dl>
        <div v-if="canWithdraw(current)" class="withdraw-area">
          <el-input
            v-model="withdrawReason"
            placeholder="撤回原因（可选）"
          /><el-button
            type="danger"
            :loading="withdrawing"
            data-testid="withdraw-adoption"
            @click="submitWithdraw"
            >撤回申请</el-button
          >
        </div>
      </div>
    </el-dialog>
  </main>
</template>

<script>
import StatusPanel from '@/components/StatusPanel.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import FilterBar from '@/components/ui/FilterBar.vue'
import LoadingState from '@/components/ui/LoadingState.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import StatusTimeline from '@/components/ui/StatusTimeline.vue'
import OrderStatusCard from '@/components/order/OrderStatusCard.vue'
import emptyPetImage from '@/assets/illustrations/empty-pet.png'
import { listMyAdoptions, getAdoption, withdrawAdoption } from '@/api/adoption'

const STATUS_LABELS = {
  PENDING: '待审核',
  APPROVED: '已通过',
  REJECTED: '已驳回',
  WITHDRAWN: '已撤回',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
}
const STATUS_CLASSES = {
  PENDING: 'warning',
  APPROVED: 'info',
  REJECTED: 'error',
  WITHDRAWN: 'info',
  COMPLETED: 'success',
  CANCELLED: 'info',
}

export default {
  name: 'MyAdoptionsView',
  components: {
    StatusPanel,
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
      applications: [],
      total: 0,
      loading: false,
      error: '',
      filters: { status: undefined },
      page: { page: 1, size: 10 },
      showDetail: false,
      current: null,
      withdrawReason: '',
      withdrawing: false,
      emptyPetImage,
      statusOptions: Object.entries(STATUS_LABELS).map(([value, label]) => ({
        value,
        label,
      })),
    }
  },
  computed: {
    timelineActive() {
      return this.current?.status === 'PENDING'
        ? 1
        : this.current?.status === 'APPROVED'
        ? 2
        : 3
    },
    timelineItems() {
      return [
        { title: '提交申请', description: '平台已收到申请资料' },
        {
          title: '平台审核',
          description:
            this.current?.status === 'PENDING'
              ? '正在核验照护条件'
              : '审核阶段已更新',
        },
        {
          title: '沟通交接',
          description: this.current?.nextStep || '通过后安排双方沟通',
        },
        { title: '完成领养', description: '确认交接后完成流程' },
      ]
    },
  },
  created() {
    this.loadAdoptions()
  },
  methods: {
    async loadAdoptions() {
      this.loading = true
      this.error = ''
      try {
        const response = await listMyAdoptions({
          status: this.filters.status || undefined,
          page: this.page.page,
          size: this.page.size,
        })
        this.applications = response.data.items || []
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
        const response = await getAdoption(row.id)
        this.current = response.data
        this.withdrawReason = ''
        this.showDetail = true
      } catch (error) {
        this.$message && this.$message.error(error.message)
      }
    },
    canWithdraw(application) {
      return (
        application &&
        (application.status === 'PENDING' || application.status === 'APPROVED')
      )
    },
    async submitWithdraw() {
      if (!this.current) return
      this.withdrawing = true
      try {
        const response = await withdrawAdoption(this.current.id, {
          reason: this.withdrawReason ? this.withdrawReason.trim() : undefined,
          version: this.current.version,
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
    nextStep(status) {
      return (
        {
          PENDING: '等待平台审核',
          APPROVED: '留意交接安排',
          REJECTED: '查看原因后重新评估',
          WITHDRAWN: '可重新寻找伙伴',
          COMPLETED: '开始新的陪伴生活',
          CANCELLED: '流程已经结束',
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
.adoption-detail {
  display: grid;
  gap: 20px;
}
.detail-list {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
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
}
.withdraw-area {
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
  .withdraw-area {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
