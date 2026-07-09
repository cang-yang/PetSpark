<template>
  <section class="training-list-page">
    <el-card>
      <div class="page-header">
        <div>
          <h2>训练服务</h2>
          <p>选择适合宠物的行为训练项目，提交训练目标与注意事项。</p>
        </div>
        <router-link to="/my/training/bookings">我的训练预约</router-link>
      </div>
      <div class="toolbar">
        <el-input v-model="filters.keyword" placeholder="搜索训练项目" clearable />
        <el-button type="primary" @click="loadItems">搜索</el-button>
      </div>
      <div v-if="items.length" class="training-grid" data-testid="training-list">
        <article v-for="item in items" :key="item.id" class="training-card" :data-testid="`training-${item.id}`">
          <router-link :to="`/training/${item.id}`">
            <h3>{{ item.name }}</h3>
          </router-link>
          <p class="price">￥{{ item.basePrice }}</p>
          <p v-if="item.description">{{ item.description }}</p>
          <p v-if="item.qualification" class="meta">资质：{{ item.qualification }}</p>
          <p v-if="item.availabilityNote" class="meta">时段：{{ item.availabilityNote }}</p>
        </article>
      </div>
      <p v-else-if="!loading" data-testid="training-empty">暂无可用训练服务</p>
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
import { listTrainingItems } from '@/api/training'

export default {
  name: 'TrainingServiceListView',
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
        const response = await listTrainingItems({
          keyword: this.filters.keyword || undefined,
          status: 'ACTIVE',
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
    }
  }
}
</script>

<style scoped>
.training-list-page { max-width: 1100px; margin: 24px auto; }
.page-header { display: flex; justify-content: space-between; gap: 16px; align-items: flex-start; margin-bottom: 16px; }
.page-header h2 { margin: 0 0 6px; }
.page-header p { margin: 0; color: #606266; }
.toolbar { display: flex; gap: 12px; margin-bottom: 20px; }
.training-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(240px, 1fr)); gap: 16px; }
.training-card { padding: 16px; background: #fff; border: 1px solid #ebeef5; border-radius: 8px; }
.price { color: #f56c6c; font-weight: 700; }
.meta { color: #606266; font-size: 13px; }
</style>
