<template>
  <section>
    <el-card v-if="goods">
      <img class="cover" :src="goodsCover" :alt="goods.name" @error="useGoodsFallback" />
      <h2>{{ goods.name }}</h2>
      <p>{{ goods.description }}</p>
      <p>SKU：{{ goods.sku }}</p>
      <p class="price">￥{{ goods.price }}</p>
      <p>库存：{{ goods.stock }}</p>
    </el-card>

    <el-card v-if="goods" class="buy-card" data-testid="order-create-section">
      <h3>立即购买</h3>
      <el-form :model="orderForm" label-width="80px">
        <el-form-item label="数量">
          <el-input-number v-model="orderForm.quantity" :min="1" :max="99" />
        </el-form-item>
        <el-form-item label="收货人"><el-input v-model="orderForm.recipientName" /></el-form-item>
        <el-form-item label="手机号"><el-input v-model="orderForm.recipientPhone" /></el-form-item>
        <el-form-item label="地址"><el-input v-model="orderForm.address" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <div class="buy-actions">
        <el-button @click="preview" :loading="previewing">预览</el-button>
        <el-button type="primary" @click="submit" :loading="submitting">提交订单</el-button>
      </div>
      <p v-if="previewResult" class="preview-result">
        <span v-if="previewResult.available">合计：￥{{ previewResult.totalAmount }}</span>
        <span v-else class="warn">暂不可下单：{{ previewResult.unavailableReason }}</span>
      </p>
    </el-card>
  </section>
</template>

<script>
import { getGoods } from '@/api/catalog'
import { createOrder, previewOrder } from '@/api/orders'
import goodsPlaceholder from '@/assets/placeholders/pet-cat.png'
import { getDemoGoodsImage } from '@/utils/demoContentAssets'

export default {
  name: 'GoodsDetailView',
  data() {
    return {
      goods: null,
      orderForm: { quantity: 1, recipientName: '', recipientPhone: '', address: '' },
      previewResult: null,
      previewing: false,
      submitting: false
    }
  },
  created() {
    this.loadGoods()
  },
  computed: {
    goodsCover() {
      return (this.goods && (this.goods.coverUrl || getDemoGoodsImage(this.goods))) || goodsPlaceholder
    }
  },
  methods: {
    useGoodsFallback(event) {
      if (event && event.target && event.target.dataset.fallbackApplied !== 'true') {
        event.target.dataset.fallbackApplied = 'true'
        event.target.src = goodsPlaceholder
      }
    },
    async loadGoods() {
      try {
        const response = await getGoods(this.$route.params.id)
        this.goods = response.data
      } catch (error) {
        this.$message && this.$message.error(error.message)
      }
    },
    buildPayload() {
      return {
        lines: [{ goodsId: this.$route.params.id, quantity: this.orderForm.quantity }],
        recipientName: this.orderForm.recipientName,
        recipientPhone: this.orderForm.recipientPhone,
        address: this.orderForm.address
      }
    },
    async preview() {
      this.previewing = true
      try {
        const response = await previewOrder(this.buildPayload())
        this.previewResult = response.data
      } catch (error) {
        this.$message && this.$message.error(error.message)
      } finally {
        this.previewing = false
      }
    },
    async submit() {
      if (!this.orderForm.recipientName || !this.orderForm.recipientPhone || !this.orderForm.address) {
        this.$message && this.$message.warning('请填写收货人、手机号与地址')
        return
      }
      this.submitting = true
      try {
        const idempotencyKey = (window.crypto && window.crypto.randomUUID)
          ? window.crypto.randomUUID()
          : 'ord-' + Date.now() + '-' + Math.random().toString(36).slice(2)
        const response = await createOrder(this.buildPayload(), idempotencyKey)
        this.$message && this.$message.success('订单已创建：' + response.data.orderNo)
        this.$router && this.$router.push('/my/orders')
      } catch (error) {
        this.$message && this.$message.error(error.message)
      } finally {
        this.submitting = false
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
.buy-card {
  margin-top: 16px;
}
.buy-actions {
  display: flex;
  gap: 12px;
  margin-top: 12px;
}
.preview-result {
  margin-top: 12px;
  font-weight: 600;
}
.preview-result .warn {
  color: #e6a23c;
}
</style>
