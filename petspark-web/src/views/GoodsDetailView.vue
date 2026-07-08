<template>
  <section>
    <el-card v-if="goods">
      <img v-if="goods.coverUrl" class="cover" :src="goods.coverUrl" :alt="goods.name" />
      <h2>{{ goods.name }}</h2>
      <p>{{ goods.description }}</p>
      <p>SKU：{{ goods.sku }}</p>
      <p class="price">￥{{ goods.price }}</p>
      <p>库存：{{ goods.stock }}</p>
    </el-card>
  </section>
</template>

<script>
import { getGoods } from '@/api/catalog'

export default {
  name: 'GoodsDetailView',
  data() {
    return { goods: null }
  },
  created() {
    this.loadGoods()
  },
  methods: {
    async loadGoods() {
      try {
        const response = await getGoods(this.$route.params.id)
        this.goods = response.data
      } catch (error) {
        this.$message && this.$message.error(error.message)
      }
    }
  }
}
</script>

<style scoped>
.cover {
  max-width: 360px;
  width: 100%;
  border-radius: 8px;
}
.price {
  color: #f56c6c;
  font-size: 22px;
  font-weight: 700;
}
</style>
