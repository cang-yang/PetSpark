<template>
  <section class="services-page">
    <el-card>
      <div class="toolbar">
        <el-input v-model="filters.keyword" placeholder="搜索服务" clearable />
        <el-select v-model="filters.kind" placeholder="服务类别" clearable>
          <el-option label="通用服务" value="GENERIC" />
          <el-option label="训练" value="TRAINING" />
          <el-option label="美容" value="BEAUTY" />
          <el-option label="医疗" value="MEDICAL" />
        </el-select>
        <el-button type="primary" @click="loadItems">搜索</el-button>
      </div>
      <div v-if="items.length" class="service-grid" data-testid="service-list">
        <article v-for="item in items" :key="item.id" class="service-card" :data-testid="`service-${item.id}`">
          <router-link :to="`/services/${item.id}`">
            <h3>{{ item.name }}</h3>
          </router-link>
          <p class="kind">{{ kindLabel(item.kind) }}</p>
          <p class="price">￥{{ item.basePrice }}</p>
          <p v-if="item.qualification" class="qualification">资质：{{ item.qualification }}</p>
          <p v-if="item.availabilityNote" class="availability">时段：{{ item.availabilityNote }}</p>
        </article>
      </div>
      <p v-else-if="!loading" data-testid="service-empty">暂无可用服务</p>
      <el-pagination
        v-if="total > page.size"
        :current-page="page.page"
        :page-size="page.size"
        :total="total"
        layout="prev, pager, next"
        @current-change="changePage" />
    </el-card>
  </section>
</template>

<script>
import { listServiceItems } from '@/api/service'

export default {
  name: 'ServiceListView',
  data() {
    return {
      loading: false,
      items: [],
      total: 0,
      filters: { keyword: undefined, kind: undefined },
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
          keyword: this.filters.keyword || undefined,
          kind: this.filters.kind || undefined,
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
    },
    changePage(page) {
      this.page.page = page
      this.loadItems()
    },
    kindLabel(kind) {
      const labels = { GENERIC: '通用服务', TRAINING: '训练', BEAUTY: '美容', MEDICAL: '医疗' }
      return labels[kind] || kind || ''
    }
  }
}
</script>

<style scoped>
.toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
}
.service-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 16px;
}
.service-card {
  padding: 16px;
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 8px;
}
.kind {
  color: #909399;
}
.price {
  color: #f56c6c;
  font-weight: 700;
}
.qualification, .availability {
  color: #606266;
  font-size: 13px;
}
</style>
