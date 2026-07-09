<template>
  <section class="adoptable-pets">
    <h2>可领养宠物</h2>
    <div class="toolbar">
      <el-input v-model="filters.keyword" placeholder="名称关键词" clearable @change="loadPets" />
      <el-select v-model="filters.species" placeholder="物种" clearable @change="loadPets">
        <el-option label="狗" value="DOG" />
        <el-option label="猫" value="CAT" />
      </el-select>
      <el-button type="primary" @click="loadPets">查询</el-button>
    </div>

    <el-table :data="pets" data-testid="adoptable-pets-table" v-loading="loading">
      <el-table-column prop="name" label="名称" />
      <el-table-column prop="species" label="物种" />
      <el-table-column prop="breedName" label="品种" />
      <el-table-column prop="ownershipType" label="归属类型" />
      <el-table-column label="更新时间">
        <template slot-scope="{ row }">{{ formatTime(row.infoUpdatedAt) }}</template>
      </el-table-column>
      <el-table-column label="操作">
        <template slot-scope="{ row }">
          <el-button size="mini" type="primary" @click="openApply(row)">申请领养</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog title="申请领养" :visible.sync="showApply" width="560px">
      <div v-if="current" class="apply-form">
        <p><strong>宠物：</strong>{{ current.name }}（{{ current.breedName || current.species }}）</p>
        <el-input
          v-model="statement"
          type="textarea"
          :rows="4"
          placeholder="请说明您的领养条件、住所与时间安排（不超过 1000 字）"
          data-testid="adoption-statement"
        />
        <el-input
          v-model="profileSnapshot"
          type="textarea"
          :rows="2"
          placeholder="可选：个人资料快照（不超过 500 字，不要填写敏感信息）"
        />
      </div>
      <div slot="footer">
        <el-button @click="showApply = false">取消</el-button>
        <el-button type="primary" :loading="submitting" data-testid="submit-adoption" @click="submitApply">提交申请</el-button>
      </div>
    </el-dialog>
  </section>
</template>

<script>
import { listAdoptablePets, createAdoptionApplication } from '@/api/adoption'

export default {
  name: 'AdoptablePetsView',
  data() {
    return {
      pets: [],
      total: 0,
      loading: false,
      filters: { keyword: undefined, species: undefined },
      page: { page: 1, size: 10 },
      showApply: false,
      current: null,
      statement: '',
      profileSnapshot: '',
      submitting: false
    }
  },
  created() {
    this.loadPets()
  },
  methods: {
    async loadPets() {
      this.loading = true
      try {
        const response = await listAdoptablePets({
          keyword: this.filters.keyword || undefined,
          species: this.filters.species || undefined,
          page: this.page.page,
          size: this.page.size
        })
        this.pets = response.data.items || []
        this.total = response.data.total || 0
      } catch (error) {
        this.$message && this.$message.error(error.message)
      } finally {
        this.loading = false
      }
    },
    openApply(pet) {
      this.current = pet
      this.statement = ''
      this.profileSnapshot = ''
      this.showApply = true
    },
    async submitApply() {
      if (!this.current) return
      if (!this.statement || !this.statement.trim()) {
        this.$message && this.$message.warning('请填写申请说明')
        return
      }
      this.submitting = true
      const idempotencyKey = window.crypto && window.crypto.randomUUID
        ? window.crypto.randomUUID()
        : 'adopt-' + Date.now() + '-' + Math.random().toString(16).slice(2)
      try {
        await createAdoptionApplication(
          {
            petId: this.current.id,
            statement: this.statement.trim(),
            profileSnapshot: this.profileSnapshot ? this.profileSnapshot.trim() : undefined
          },
          idempotencyKey
        )
        this.$message && this.$message.success('申请已提交')
        this.showApply = false
        this.$router.push({ name: 'my-adoptions' })
      } catch (error) {
        this.$message && this.$message.error(error.message)
      } finally {
        this.submitting = false
      }
    },
    formatTime(value) {
      if (!value) return ''
      const date = typeof value === 'string' ? new Date(value) : value
      if (Number.isNaN(date.getTime())) return String(value)
      const pad = (n) => String(n).padStart(2, '0')
      return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
    }
  }
}
</script>

<style scoped>
.adoptable-pets { max-width: 1100px; margin: 24px auto; }
.toolbar { display: flex; gap: 12px; margin-bottom: 16px; }
.apply-form { display: flex; flex-direction: column; gap: 12px; }
</style>
