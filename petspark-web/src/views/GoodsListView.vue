<template>
  <main class="goods-page">
    <page-header
      title="宠物好物"
      description="为日常喂养、玩耍和照护挑选实用商品。"
      ><template #actions
        ><router-link class="page-link" to="/my/orders"
          >查看我的订单</router-link
        ></template
      ></page-header
    >
    <filter-bar
      ><el-input
        v-model="filters.keyword"
        placeholder="搜索商品"
        clearable
        @keyup.enter.native="loadGoods"
      /><template #actions
        ><el-button type="primary" @click="loadGoods"
          >查找商品</el-button
        ></template
      ></filter-bar
    >
    <loading-state v-if="loading" text="正在整理上架商品…" /><error-state
      v-else-if="error"
      title="商品列表暂时没有加载出来"
      :description="error"
      @retry="loadGoods"
    /><empty-state
      v-else-if="!goods.length"
      title="暂无上架商品"
      description="换一个关键词，或稍后再来看看新到好物。"
    />
    <section v-else class="goods-grid" data-testid="goods-list">
      <article
        v-for="item in goods"
        :key="item.id"
        class="goods-card"
        :data-testid="`goods-${item.id}`"
      >
        <router-link :to="`/goods/${item.id}`" class="goods-card__visual"
          ><img
            :src="goodsImage(item)"
            :alt="item.name"
            @error="useGoodsFallback"
          /><span :class="['stock', { 'stock--low': item.stock < 5 }]">{{
            item.stock > 0 ? `库存 ${item.stock}` : '暂时缺货'
          }}</span></router-link
        >
        <div class="goods-card__body">
          <p class="sku">{{ item.sku }}</p>
          <h2>{{ item.name }}</h2>
          <footer>
            <strong>￥{{ item.price }}</strong
            ><router-link :to="`/goods/${item.id}`">查看详情</router-link>
          </footer>
        </div>
      </article>
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
import { listGoods } from '@/api/catalog'
import PageHeader from '@/components/ui/PageHeader.vue'
import FilterBar from '@/components/ui/FilterBar.vue'
import LoadingState from '@/components/ui/LoadingState.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import goodsPlaceholder from '@/assets/placeholders/pet-cat.png'
import { getDemoGoodsImage } from '@/utils/demoContentAssets'
export default {
  name: 'GoodsListView',
  components: { PageHeader, FilterBar, LoadingState, EmptyState, ErrorState },
  data() {
    return {
      loading: false,
      error: '',
      goods: [],
      total: 0,
      filters: { keyword: undefined, categoryId: undefined },
      page: { page: 1, size: 12 },
      goodsPlaceholder,
    }
  },
  created() {
    this.loadGoods()
  },
  methods: {
    goodsImage(item) {
      return item.coverUrl || getDemoGoodsImage(item) || this.goodsPlaceholder
    },
    useGoodsFallback(event) {
      if (event && event.target && event.target.dataset.fallbackApplied !== 'true') {
        event.target.dataset.fallbackApplied = 'true'
        event.target.src = this.goodsPlaceholder
      }
    },
    async loadGoods() {
      this.loading = true
      this.error = ''
      try {
        const response = await listGoods({
          keyword: this.filters.keyword || undefined,
          categoryId: this.filters.categoryId || undefined,
          page: this.page.page,
          size: this.page.size,
        })
        this.goods = response.data.items || []
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
      this.loadGoods()
    },
  },
}
</script>
<style scoped>
.goods-page {
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
.goods-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(210px, 1fr));
  gap: 22px;
}
.goods-card {
  overflow: hidden;
  background: var(--ps-color-surface);
  border: 1px solid var(--ps-color-border);
  border-radius: var(--ps-radius-lg);
  box-shadow: var(--ps-shadow-card);
}
.goods-card__visual {
  position: relative;
  display: block;
  aspect-ratio: 4/3;
  overflow: hidden;
  background: var(--ps-color-cream);
}
.goods-card__visual img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform var(--ps-motion-normal) var(--ps-ease-out);
}
.goods-card:hover img {
  transform: scale(1.03);
}
.stock {
  position: absolute;
  right: 12px;
  bottom: 12px;
  padding: 4px 9px;
  color: var(--ps-color-green);
  background: rgba(255, 255, 255, 0.9);
  border-radius: var(--ps-radius-pill);
  font-size: 12px;
  font-weight: 700;
}
.stock--low {
  color: var(--ps-color-warning);
}
.goods-card__body {
  padding: 17px;
}
.goods-card__body h2 {
  margin: 3px 0 16px;
  font-size: 18px;
}
.sku {
  margin: 0;
  color: var(--ps-color-muted);
  font-size: 12px;
}
.goods-card footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.goods-card footer strong {
  color: var(--ps-color-pink);
  font-size: 19px;
}
.goods-card footer a {
  color: var(--ps-color-pink);
  font-weight: 700;
  text-decoration: none;
}
.el-pagination {
  margin-top: 24px;
  text-align: center;
}
@media (max-width: 640px) {
  .goods-page {
    padding: 24px 16px 40px;
  }
  .ps-filter-bar .el-input {
    width: 100%;
  }
}
@media (prefers-reduced-motion: reduce) {
  .goods-card__visual img {
    transition: none;
  }
  .goods-card:hover img {
    transform: none;
  }
}
</style>
