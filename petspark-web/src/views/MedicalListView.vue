<template>
  <main class="service-page">
    <page-header
      title="宠物医疗"
      description="预约线下检查与专业照护，出现紧急症状时请直接联系动物医院。"
    />
    <aside class="medical-boundary" role="note">
      <strong>服务边界</strong><span>线下预约，不提供在线诊断或处方。</span>
    </aside>
    <filter-bar
      ><el-input
        v-model="filters.keyword"
        placeholder="搜索医疗服务"
        clearable
        @keyup.enter.native="loadItems"
      /><template #actions
        ><el-button type="primary" @click="loadItems"
          >查找医疗</el-button
        ></template
      ></filter-bar
    >
    <loading-state v-if="loading" text="正在准备医疗项目…" /><error-state
      v-else-if="error"
      title="医疗服务暂时没有加载出来"
      :description="error"
      @retry="loadItems"
    /><empty-state
      v-else-if="!items.length"
      title="暂无医疗服务"
      description="换一个关键词，或联系线下动物医院获取帮助。"
    />
    <section v-else class="service-grid" data-testid="medical-list">
      <service-card
        v-for="item in items"
        :key="item.id"
        :service="item"
        :to="{ name: 'medical-detail', params: { id: item.id } }"
        :data-testid="`medical-${item.id}`"
        notice="线下预约，不提供在线诊断或处方"
        ><template
          v-if="item.medicalProfile && item.medicalProfile.careScope"
          #details
          ><p class="profile-note">
            服务范围 · {{ item.medicalProfile.careScope }}
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
  name: 'MedicalListView',
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
          kind: 'MEDICAL',
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
.medical-boundary {
  display: flex;
  gap: 10px;
  align-items: center;
  padding: 13px 16px;
  margin-bottom: 18px;
  color: var(--ps-color-warning);
  background: #fff8e8;
  border: 1px solid #f5dfb8;
  border-radius: var(--ps-radius-md);
  font-size: 13px;
}
.medical-boundary strong {
  flex: 0 0 auto;
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
  .medical-boundary {
    align-items: flex-start;
    flex-direction: column;
  }
  .ps-filter-bar .el-input {
    width: 100%;
  }
}
</style>
