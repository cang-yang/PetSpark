<template>
  <section class="medical-page">
    <el-card>
      <div class="page-header">
        <div>
          <h2>宠物医疗</h2>
          <p>仅展示医疗类服务项目，预约会复用服务预约履约流程。</p>
        </div>
        <el-input v-model="filters.keyword" placeholder="搜索医疗服务" clearable @keyup.enter.native="loadItems" />
        <el-button type="primary" @click="loadItems">搜索</el-button>
      </div>
      <div v-if="items.length" class="medical-grid" data-testid="medical-list">
        <article v-for="item in items" :key="item.id" class="medical-card" :data-testid="`medical-${item.id}`">
          <router-link :to="{ name: 'medical-detail', params: { id: item.id } }">
            <h3>{{ item.name }}</h3>
          </router-link>
          <p class="price">￥{{ item.basePrice }}</p>
          <p v-if="item.qualification">资质：{{ item.qualification }}</p>
          <p v-if="item.availabilityNote">时段：{{ item.availabilityNote }}</p>
          <p v-if="item.medicalProfile && item.medicalProfile.careScope">
            诊疗范围：{{ item.medicalProfile.careScope }}
          </p>
        </article>
      </div>
      <p v-else-if="!loading" data-testid="medical-empty">暂无医疗服务</p>
    </el-card>
  </section>
</template>

<script>
import { listServiceItems } from '@/api/service'

export default {
  name: 'MedicalListView',
  data() {
    return {
      loading: false,
      items: [],
      total: 0,
      filters: { keyword: undefined },
      page: { page: 1, size: 12 }
    }
  },
  created() {
    this.loadItems()
  },
  methods: {
    async loadItems() {
      this.loading = true
      try {
        const response = await listServiceItems({
          kind: 'MEDICAL',
          status: 'ACTIVE',
          keyword: this.filters.keyword || undefined,
          page: this.page.page,
          size: this.page.size
        })
        this.items = response.data.items || []
        this.total = response.data.total || 0
      } catch (error) {
        this.$message && this.$message.error(error.message)
      } finally {
        this.loading = false
      }
    }
  }
}
</script>

<style scoped>
.medical-page { max-width: 1100px; margin: 24px auto; }
.page-header { display: flex; gap: 12px; align-items: center; margin-bottom: 20px; }
.page-header h2 { margin: 0; }
.page-header p { margin: 4px 0 0; color: #606266; }
.page-header .el-input { max-width: 280px; margin-left: auto; }
.medical-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(240px, 1fr)); gap: 16px; }
.medical-card { padding: 16px; background: #fff; border: 1px solid #ebeef5; border-radius: 8px; }
.price { color: #f56c6c; font-weight: 700; }
</style>
