<template>
  <section class="goods-page">
    <el-card>
      <div class="toolbar">
        <el-input v-model="filters.keyword" placeholder="搜索商品" clearable />
        <el-button type="primary" @click="loadGoods">搜索</el-button>
      </div>
      <div v-if="goods.length" class="goods-grid" data-testid="goods-list">
        <article v-for="item in goods" :key="item.id" class="goods-card" :data-testid="`goods-${item.id}`">
          <router-link :to="`/goods/${item.id}`">
            <img v-if="item.coverUrl" :src="item.coverUrl" :alt="item.name" />
            <h3>{{ item.name }}</h3>
          </router-link>
          <p class="sku">{{ item.sku }}</p>
          <p class="price">￥{{ item.price }}</p>
          <p>库存 {{ item.stock }}</p>
        </article>
      </div>
      <p v-else-if="!loading" data-testid="goods-empty">暂无上架商品</p>
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
import { listGoods } from '@/api/catalog'

export default {
  name: 'GoodsListView',
  data() {
    return {
      loading: false,
      goods: [],
      total: 0,
      filters: { keyword: undefined, categoryId: undefined },
      page: { page: 1, size: 12 }
    }
  },
  created() {
    this.loadGoods()
  },
  methods: {
    async loadGoods() {
      this.loading = true
      try {
        const response = await listGoods({
          keyword: this.filters.keyword || undefined,
          categoryId: this.filters.categoryId || undefined,
          page: this.page.page,
          size: this.page.size
        })
        this.goods = response.data.items || []
        this.total = response.data.total || 0
      } catch (error) {
        this.$message && this.$message.error(error.message)
      } finally {
        this.loading = false
      }
    },
    changePage(page) {
      this.page.page = page
      this.loadGoods()
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
.goods-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 16px;
}
.goods-card {
  padding: 16px;
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 8px;
}
.goods-card img {
  width: 100%;
  height: 120px;
  object-fit: cover;
  border-radius: 6px;
}
.sku {
  color: #909399;
}
.price {
  color: #f56c6c;
  font-weight: 700;
}
</style>
