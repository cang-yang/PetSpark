<template>
  <section class="admin-dashboard">
    <AdminPageHeader eyebrow="运营总览" title="统计仪表盘" description="查看平台核心指标与业务状态分布。" />

    <LoadingState v-if="loading" data-testid="dashboard-loading" text="正在汇总平台数据…" />
    <ErrorState
      v-else-if="error"
      data-testid="dashboard-error"
      title="仪表盘数据暂时不可用"
      :description="error"
      @retry="loadSummary"
    />
    <template v-else>
      <!-- 计数指标卡片 -->
      <div class="metrics-grid" data-testid="dashboard-metrics">
        <MetricCard
          v-for="m in metrics"
          :key="m.key"
          :data-testid="'metric-' + m.key"
          :label="m.label"
          :value="m.value"
        />
      </div>

      <!-- 状态分布饼图 -->
      <div class="charts-grid" data-testid="dashboard-charts">
        <div
          v-for="d in distributions"
          :key="d.key"
          class="chart-card"
          :data-testid="'chart-' + d.key"
        >
          <h3>{{ d.label }}</h3>
          <div :ref="'chart-' + d.key" class="chart-canvas"></div>
        </div>
      </div>
    </template>
  </section>
</template>

<script>
import * as echarts from 'echarts'
import { getDashboardSummary } from '@/api/dashboard'
import AdminPageHeader from '@/components/ui/AdminPageHeader.vue'
import LoadingState from '@/components/ui/LoadingState.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import MetricCard from '@/components/ui/MetricCard.vue'

const STATUS_COLORS = {
  CREATED: '#409EFF',
  PROCESSING: '#E6A23C',
  COMPLETED: '#67C23A',
  CANCELLED: '#909399',
  EXCEPTION: '#F56C6C',
  PENDING: '#409EFF',
  APPROVED: '#67C23A',
  REJECTED: '#F56C6C',
  WITHDRAWN: '#909399',
  CONFIRMED: '#67C23A',
  IN_SERVICE: '#E6A23C',
  TERMINATED: '#F56C6C',
  PENDING_CONFIRMATION: '#409EFF',
  IN_PROGRESS: '#E6A23C',
  PUBLISHED: '#67C23A',
  HIDDEN: '#909399',
  SUBMITTED: '#409EFF',
  ASSIGNED: '#E6A23C',
  IN_RESCUE: '#E6A23C',
  RESOLVED: '#67C23A',
  CLOSED: '#909399',
  SENT: '#67C23A',
  DEAD: '#F56C6C',
  ACTIVE: '#67C23A',
  DRAFT: '#909399',
  INACTIVE: '#E6A23C'
}

export default {
  name: 'AdminDashboardView',
  components: { AdminPageHeader, LoadingState, ErrorState, MetricCard },
  data() {
    return {
      loading: false,
      error: null,
      metrics: [],
      distributions: [],
      chartInstances: []
    }
  },
  created() {
    this.loadSummary()
  },
  beforeDestroy() {
    this.disposeCharts()
  },
  methods: {
    async loadSummary() {
      this.loading = true
      this.error = null
      try {
        const res = await getDashboardSummary()
        const data = res.data || {}
        this.metrics = data.metrics || []
        this.distributions = data.distributions || []
      } catch (e) {
        this.error = (e && e.message) || '加载仪表盘数据失败'
      } finally {
        this.loading = false
        if (!this.error) this.$nextTick(() => this.renderCharts())
      }
    },
    renderCharts() {
      this.disposeCharts()
      for (const d of this.distributions) {
        const el = this.$refs['chart-' + d.key]
        if (!el || !el[0]) continue
        const instance = echarts.init(el[0])
        instance.setOption({
          tooltip: { trigger: 'item' },
          legend: { bottom: 0, type: 'scroll' },
          series: [{
            type: 'pie',
            radius: ['40%', '70%'],
            avoidLabelOverlap: true,
            label: { show: true, formatter: '{b}: {c}' },
            data: (d.items || []).map(b => ({
              name: b.status,
              value: b.count,
              itemStyle: { color: STATUS_COLORS[b.status] || '#909399' }
            }))
          }]
        })
        this.chartInstances.push(instance)
      }
    },
    disposeCharts() {
      for (const inst of this.chartInstances) {
        try { inst.dispose() } catch (e) { /* noop */ }
      }
      this.chartInstances = []
    }
  }
}
</script>

<style scoped>
.admin-dashboard {
  display: grid;
  gap: 20px;
}
.metrics-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: 16px;
  margin-bottom: 4px;
}
.metrics-grid .ps-metric-card {
  min-width: 0;
}
.charts-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 24px;
}
.chart-card {
  background: #fff;
  border: 1px solid var(--ps-color-border);
  border-radius: var(--ps-radius-md);
  padding: 18px;
}
.chart-card h3 {
  margin: 0 0 12px 0;
  font-size: 15px;
  color: #303133;
}
.chart-canvas {
  width: 100%;
  height: 260px;
}
</style>
