<template>
  <main class="service-page">
    <page-header
      title="宠物服务"
      description="从日常照护到专业服务，为每个生活场景找到合适安排。"
    />
    <filter-bar
      ><el-input
        v-model="filters.keyword"
        placeholder="搜索服务"
        clearable
        @keyup.enter.native="loadItems"
      /><el-select v-model="filters.kind" placeholder="全部类别" clearable
        ><el-option label="通用服务" value="GENERIC" /><el-option
          label="训练"
          value="TRAINING" /><el-option label="美容" value="BEAUTY" /><el-option
          label="医疗"
          value="MEDICAL" /></el-select
      ><template #actions
        ><el-button type="primary" @click="loadItems"
          >查找服务</el-button
        ></template
      ></filter-bar
    >
    <loading-state v-if="loading" text="正在准备服务项目…" />
    <error-state
      v-else-if="error"
      title="服务列表暂时没有加载出来"
      :description="error"
      @retry="loadItems"
    />
    <empty-state
      v-else-if="!items.length"
      title="暂无可用服务"
      description="调整关键词或服务类别，稍后再来看看。"
    />
    <section v-else class="service-grid" data-testid="service-list">
      <service-card
        v-for="item in items"
        :key="item.id"
        :service="item"
        :to="`/services/${item.id}`"
        :data-testid="`service-${item.id}`"
      />
    </section>
    <el-pagination
      v-if="total > page.size"
      :current-page="page.page"
      :page-size="page.size"
      :total="total"
      layout="prev, pager, next"
      @current-change="changePage"
    />
  </main>
</template>
<script>
import { listServiceItems } from '@/api/service'
import PageHeader from '@/components/ui/PageHeader.vue'
import FilterBar from '@/components/ui/FilterBar.vue'
import LoadingState from '@/components/ui/LoadingState.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import ServiceCard from '@/components/service/ServiceCard.vue'
export default {
  name: 'ServiceListView',
  components: {
    PageHeader,
    FilterBar,
    LoadingState,
    EmptyState,
    ErrorState,
    ServiceCard,
  },
  data() {
    return {
      loading: false,
      error: '',
      items: [],
      total: 0,
      filters: { keyword: undefined, kind: undefined },
      page: { page: 1, size: 12 },
    }
  },
  created() {
    this.loadItems()
  },
  methods: {
    async loadItems() {
      this.loading = true
      this.error = ''
      try {
        const response = await listServiceItems({
          keyword: this.filters.keyword || undefined,
          kind: this.filters.kind || undefined,
          page: this.page.page,
          size: this.page.size,
        })
        this.items = response.data.items || []
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
    changePage(page) {
      this.page.page = page
      this.loadItems()
    },
    kindLabel(kind) {
      return (
        {
          GENERIC: '通用服务',
          TRAINING: '训练',
          BEAUTY: '美容',
          MEDICAL: '医疗',
        }[kind] ||
        kind ||
        ''
      )
    },
  },
}
</script>
<style scoped>
.service-page {
  width: min(100%, var(--ps-page-max));
  margin: 0 auto;
  padding: 36px 24px 56px;
}
.ps-filter-bar .el-input {
  width: min(320px, 100%);
}
.ps-filter-bar .el-select {
  width: 180px;
}
.service-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(250px, 1fr));
  gap: 22px;
}
.el-pagination {
  margin-top: 24px;
  text-align: center;
}
@media (max-width: 640px) {
  .service-page {
    padding: 24px 16px 40px;
  }
  .ps-filter-bar .el-input,
  .ps-filter-bar .el-select {
    width: 100%;
  }
}
</style>
