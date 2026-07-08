<template>
  <section>
    <el-card>
      <div class="toolbar">
        <el-input v-model="filters.keyword" placeholder="SKU / 名称" clearable />
        <el-select v-model="filters.status" placeholder="状态" clearable>
          <el-option label="草稿" value="DRAFT" />
          <el-option label="上架" value="ACTIVE" />
          <el-option label="下架" value="INACTIVE" />
        </el-select>
        <el-button type="primary" @click="loadGoods">查询</el-button>
        <el-button @click="openCreate">新增商品</el-button>
      </div>
      <el-table :data="goods" data-testid="admin-goods-table">
        <el-table-column prop="sku" label="SKU" />
        <el-table-column prop="name" label="名称" />
        <el-table-column prop="price" label="价格" />
        <el-table-column prop="stock" label="库存" />
        <el-table-column prop="status" label="状态" />
        <el-table-column label="操作">
          <template slot-scope="{ row }">
            <el-button size="mini" @click="changeStatus(row, row.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE')">
              {{ row.status === 'ACTIVE' ? '下架' : '上架' }}
            </el-button>
            <el-button size="mini" @click="adjustStock(row, 1, '后台补货')">+1</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog title="新增商品" :visible.sync="showForm">
      <el-form :model="form">
        <el-form-item label="分类 ID"><el-input v-model="form.categoryId" /></el-form-item>
        <el-form-item label="SKU"><el-input v-model="form.sku" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="价格"><el-input v-model.number="form.price" /></el-form-item>
        <el-form-item label="库存"><el-input v-model.number="form.stock" /></el-form-item>
      </el-form>
      <span slot="footer">
        <el-button @click="showForm = false">取消</el-button>
        <el-button type="primary" @click="submitCreate">保存</el-button>
      </span>
    </el-dialog>
  </section>
</template>

<script>
import { adjustGoodsStock, createGoods, listAdminGoods, updateGoodsStatus } from '@/api/catalog'

export default {
  name: 'AdminGoodsView',
  data() {
    return {
      goods: [],
      total: 0,
      filters: { keyword: undefined, status: undefined },
      page: { page: 1, size: 10 },
      showForm: false,
      form: this.emptyForm()
    }
  },
  created() {
    this.loadGoods()
  },
  methods: {
    emptyForm() {
      return {
        categoryId: '',
        sku: '',
        name: '',
        description: '',
        coverFileId: null,
        price: 0,
        stock: 0,
        status: 'DRAFT',
        version: 0
      }
    },
    async loadGoods() {
      try {
        const response = await listAdminGoods({
          keyword: this.filters.keyword || undefined,
          status: this.filters.status || undefined,
          page: this.page.page,
          size: this.page.size
        })
        this.goods = response.data.items || []
        this.total = response.data.total || 0
      } catch (error) {
        this.$message && this.$message.error(error.message)
      }
    },
    openCreate() {
      this.form = this.emptyForm()
      this.showForm = true
    },
    async submitCreate() {
      try {
        await createGoods(this.form)
        this.showForm = false
        this.$message && this.$message.success('商品已创建')
        await this.loadGoods()
      } catch (error) {
        this.$message && this.$message.error(error.message)
      }
    },
    async changeStatus(row, status) {
      const response = await updateGoodsStatus(row.id, { status, version: row.version })
      Object.assign(row, response.data)
      this.$message && this.$message.success('状态已更新')
    },
    async adjustStock(row, delta, reason) {
      const response = await adjustGoodsStock(row.id, { delta, reason, version: row.version })
      Object.assign(row, response.data)
      this.$message && this.$message.success('库存已更新')
    }
  }
}
</script>

<style scoped>
.toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}
</style>
