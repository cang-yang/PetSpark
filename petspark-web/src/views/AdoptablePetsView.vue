<template>
  <main class="member-page">
    <page-header
      title="遇见待领养伙伴"
      description="认真了解彼此，再为一段长期陪伴提交申请。"
    />
    <filter-bar>
      <el-input
        v-model="filters.keyword"
        placeholder="搜索宠物名称"
        clearable
        @keyup.enter.native="loadPets"
      />
      <el-select v-model="filters.species" placeholder="全部物种" clearable>
        <el-option label="狗" value="DOG" /><el-option label="猫" value="CAT" />
        <el-option label="兔子" value="RABBIT" /><el-option
          label="鸟"
          value="BIRD"
        />
      </el-select>
      <template #actions
        ><el-button type="primary" @click="loadPets"
          >寻找伙伴</el-button
        ></template
      >
    </filter-bar>

    <loading-state v-if="loading" text="正在寻找等待回家的伙伴…" />
    <error-state
      v-else-if="error"
      title="领养列表暂时没有加载出来"
      :description="error"
      @retry="loadPets"
    />
    <empty-state
      v-else-if="!pets.length"
      title="还没有找到合适的小伙伴"
      description="调整筛选条件，或稍后再来看看新的领养信息。"
      :image="emptyPetImage"
    />
    <section v-else class="pet-grid" data-testid="adoptable-pets-table">
      <pet-card
        v-for="pet in pets"
        :key="pet.id"
        :pet="pet"
        status="等待回家"
        :description="`由${ownershipLabel(pet.ownershipType)}发布 · ${
          formatTime(pet.infoUpdatedAt) || '近期更新'
        }`"
        action-text="申请领养"
        @action="openApply"
      />
    </section>

    <el-dialog
      title="申请领养"
      :visible.sync="showApply"
      width="min(560px, 92vw)"
    >
      <div v-if="current" class="apply-form">
        <div class="apply-form__pet">
          <pet-avatar :pet="current" :size="58" />
          <div>
            <strong>{{ current.name }}</strong>
            <p>{{ current.breedName || current.species }}</p>
          </div>
        </div>
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
        <el-button @click="showApply = false">取消</el-button
        ><el-button
          type="primary"
          :loading="submitting"
          data-testid="submit-adoption"
          @click="submitApply"
          >提交申请</el-button
        >
      </div>
    </el-dialog>
  </main>
</template>

<script>
import { listAdoptablePets, createAdoptionApplication } from '@/api/adoption'
import PageHeader from '@/components/ui/PageHeader.vue'
import FilterBar from '@/components/ui/FilterBar.vue'
import LoadingState from '@/components/ui/LoadingState.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import PetCard from '@/components/pet/PetCard.vue'
import PetAvatar from '@/components/pet/PetAvatar.vue'
import emptyPetImage from '@/assets/illustrations/empty-pet.png'

export default {
  name: 'AdoptablePetsView',
  components: {
    PageHeader,
    FilterBar,
    LoadingState,
    EmptyState,
    ErrorState,
    PetCard,
    PetAvatar,
  },
  data() {
    return {
      pets: [],
      total: 0,
      loading: false,
      error: '',
      filters: { keyword: undefined, species: undefined },
      page: { page: 1, size: 10 },
      showApply: false,
      current: null,
      statement: '',
      profileSnapshot: '',
      submitting: false,
      emptyPetImage,
    }
  },
  created() {
    this.loadPets()
  },
  methods: {
    async loadPets() {
      this.loading = true
      this.error = ''
      try {
        const response = await listAdoptablePets({
          keyword: this.filters.keyword || undefined,
          species: this.filters.species || undefined,
          page: this.page.page,
          size: this.page.size,
        })
        this.pets = response.data.items || []
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
      const idempotencyKey =
        window.crypto && window.crypto.randomUUID
          ? window.crypto.randomUUID()
          : 'adopt-' + Date.now() + '-' + Math.random().toString(16).slice(2)
      try {
        await createAdoptionApplication(
          {
            petId: this.current.id,
            statement: this.statement.trim(),
            profileSnapshot: this.profileSnapshot
              ? this.profileSnapshot.trim()
              : undefined,
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
    ownershipLabel(type) {
      return (
        { PLATFORM: '平台', USER: '爱心用户', RESCUE: '救助机构' }[type] ||
        '平台'
      )
    },
    formatTime(value) {
      if (!value) return ''
      const date = new Date(value)
      if (Number.isNaN(date.getTime())) return String(value)
      return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(
        2,
        '0'
      )}-${String(date.getDate()).padStart(2, '0')}`
    },
  },
}
</script>

<style scoped>
.member-page {
  width: min(100%, var(--ps-page-max));
  margin: 0 auto;
  padding: 36px 24px 56px;
}
.ps-filter-bar .el-input {
  width: min(320px, 100%);
}
.ps-filter-bar .el-select {
  width: 180px;
}
.pet-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 22px;
}
.apply-form {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.apply-form__pet {
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 12px;
  background: var(--ps-color-cream);
  border-radius: var(--ps-radius-md);
}
.apply-form__pet p {
  margin: 0;
  color: var(--ps-color-muted);
}
@media (max-width: 640px) {
  .member-page {
    padding: 24px 16px 40px;
  }
  .ps-filter-bar .el-input,
  .ps-filter-bar .el-select {
    width: 100%;
  }
}
</style>
