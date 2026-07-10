<template>
  <main class="member-page">
    <page-header
      title="宠物目录"
      description="认识平台里的宠物伙伴，按名字或物种快速找到它。"
    />
    <filter-bar>
      <el-input
        v-model="query.keyword"
        placeholder="搜索宠物名称"
        clearable
        @keyup.enter.native="load"
      />
      <el-select v-model="query.species" placeholder="全部物种" clearable>
        <el-option label="狗" value="DOG" />
        <el-option label="猫" value="CAT" />
        <el-option label="兔子" value="RABBIT" />
        <el-option label="鸟" value="BIRD" />
      </el-select>
      <template #actions
        ><el-button type="primary" @click="load">查找伙伴</el-button></template
      >
    </filter-bar>

    <loading-state v-if="loading" text="正在寻找宠物伙伴…" />
    <error-state
      v-else-if="error"
      title="宠物目录暂时没有加载出来"
      :description="error"
      @retry="load"
    />
    <div v-else-if="pets.length === 0">
      <span class="ps-sr-only">暂无宠物</span>
      <empty-state
        title="还没有找到合适的小伙伴"
        description="换一个名字或物种试试，也可以稍后再来看看。"
        :image="emptyPetImage"
      />
    </div>
    <section v-else class="pet-grid" aria-label="宠物列表">
      <pet-card
        v-for="pet in pets"
        :key="pet.id"
        :pet="pet"
        :status="adoptionLabel(pet.adoptionStatus)"
        :description="pet.description || '每一位伙伴都有自己的性格与生活习惯。'"
      >
        <template #details
          ><span class="ps-sr-only">{{ pet.name }}</span></template
        >
      </pet-card>
    </section>
  </main>
</template>

<script>
import { listPets } from '@/api/pets'
import PageHeader from '@/components/ui/PageHeader.vue'
import FilterBar from '@/components/ui/FilterBar.vue'
import LoadingState from '@/components/ui/LoadingState.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import PetCard from '@/components/pet/PetCard.vue'
import emptyPetImage from '@/assets/illustrations/empty-pet.png'

export default {
  name: 'PetsView',
  components: {
    PageHeader,
    FilterBar,
    LoadingState,
    EmptyState,
    ErrorState,
    PetCard,
  },
  data() {
    return {
      query: { keyword: '', species: '', page: 1, size: 20 },
      pets: [],
      loading: false,
      error: '',
      emptyPetImage,
    }
  },
  created() {
    this.load()
  },
  methods: {
    async load() {
      this.loading = true
      this.error = ''
      try {
        const response = await listPets(this.query)
        this.pets = response.data.data?.items || response.data.items || []
      } catch (error) {
        this.error =
          error.response?.data?.message ||
          error.message ||
          '请检查网络连接后重试。'
      } finally {
        this.loading = false
      }
    },
    adoptionLabel(status) {
      return (
        {
          AVAILABLE: '等待回家',
          ADOPTED: '已有归属',
          NOT_AVAILABLE: '暂不可领养',
        }[status] || ''
      )
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
