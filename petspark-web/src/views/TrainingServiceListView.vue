<template>
  <main class="service-page">
    <page-header
      title="训练服务"
      description="用清晰目标和温和方法，陪小伙伴建立更轻松的相处习惯。"
      ><template #actions
        ><router-link class="page-link" to="/my/training/bookings"
          >我的训练预约</router-link
        ></template
      ></page-header
    >
    <filter-bar
      ><el-input
        v-model="filters.keyword"
        placeholder="搜索训练项目"
        clearable
        @keyup.enter.native="loadItems"
      /><template #actions
        ><el-button type="primary" @click="loadItems"
          >查找训练</el-button
        ></template
      ></filter-bar
    >
    <loading-state v-if="loading" text="正在准备训练项目…" /><error-state
      v-else-if="error"
      title="训练服务暂时没有加载出来"
      :description="error"
      @retry="loadItems"
    /><empty-state
      v-else-if="!items.length"
      title="暂无可用训练服务"
      description="换一个关键词，或稍后再来看看新的训练项目。"
    />
    <section v-else class="service-grid" data-testid="training-list">
      <service-card
        v-for="item in items"
        :key="item.id"
        :service="item"
        :to="`/training/${item.id}`"
        :data-testid="`training-${item.id}`"
        notice="训练效果因宠物状态与家庭配合而异，请按训练师建议循序进行。"
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
import { listTrainingItems } from '@/api/training'
import PageHeader from '@/components/ui/PageHeader.vue'
import FilterBar from '@/components/ui/FilterBar.vue'
import LoadingState from '@/components/ui/LoadingState.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import ServiceCard from '@/components/service/ServiceCard.vue'
export default {
  name: 'TrainingServiceListView',
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
      filters: { keyword: undefined },
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
        const response = await listTrainingItems({
          keyword: this.filters.keyword || undefined,
          status: 'ACTIVE',
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
  },
}
</script>
<style scoped>
.service-page {
  width: min(100%, var(--ps-page-max));
  margin: 0 auto;
  padding: 36px 24px 56px;
}
.page-link {
  color: var(--ps-color-pink);
  font-weight: 700;
  text-decoration: none;
}
.ps-filter-bar .el-input {
  width: min(360px, 100%);
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
  .ps-filter-bar .el-input {
    width: 100%;
  }
}
</style>
