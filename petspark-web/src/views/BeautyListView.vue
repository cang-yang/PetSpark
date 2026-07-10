<template>
  <main class="service-page">
    <page-header
      title="宠物美容"
      description="从清洁修护到造型护理，选择适合毛发与皮肤状态的服务。"
    />
    <filter-bar
      ><el-input
        v-model="filters.keyword"
        placeholder="搜索美容服务"
        clearable
        @keyup.enter.native="loadItems"
      /><template #actions
        ><el-button type="primary" @click="loadItems"
          >查找美容</el-button
        ></template
      ></filter-bar
    >
    <loading-state v-if="loading" text="正在准备美容项目…" /><error-state
      v-else-if="error"
      title="美容服务暂时没有加载出来"
      :description="error"
      @retry="loadItems"
    /><empty-state
      v-else-if="!items.length"
      title="暂无美容服务"
      description="换一个关键词，或稍后再来看看新的护理项目。"
    />
    <section v-else class="service-grid" data-testid="beauty-list">
      <service-card
        v-for="item in items"
        :key="item.id"
        :service="item"
        :to="{ name: 'beauty-detail', params: { id: item.id } }"
        :data-testid="`beauty-${item.id}`"
        ><template
          v-if="item.beautyProfile && item.beautyProfile.carePreferences"
          #details
          ><p class="profile-note">
            护理偏好 · {{ item.beautyProfile.carePreferences }}
          </p></template
        ></service-card
      >
    </section>
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
  name: 'BeautyListView',
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
        const response = await listServiceItems({
          kind: 'BEAUTY',
          status: 'ACTIVE',
          keyword: this.filters.keyword || undefined,
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
  width: min(360px, 100%);
}
.service-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(250px, 1fr));
  gap: 22px;
}
.profile-note {
  margin: 10px 0 0;
  color: var(--ps-color-muted);
  font-size: 13px;
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
