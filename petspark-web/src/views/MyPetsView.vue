<template>
  <main class="member-page">
    <page-header
      title="我的宠物"
      description="为每位小伙伴建立档案，持续记录健康和生活变化。"
    />

    <section class="create-panel" aria-labelledby="add-pet-title">
      <div>
        <p class="create-panel__eyebrow">新伙伴入住</p>
        <h2 id="add-pet-title">添加宠物档案</h2>
      </div>
      <el-form
        class="create-panel__form"
        :inline="true"
        @submit.native.prevent="submit"
      >
        <el-form-item
          ><el-input v-model="form.name" placeholder="宠物名称"
        /></el-form-item>
        <el-form-item>
          <el-select v-model="form.species">
            <el-option label="狗" value="DOG" /><el-option
              label="猫"
              value="CAT"
            />
            <el-option label="兔子" value="RABBIT" /><el-option
              label="鸟"
              value="BIRD"
            />
          </el-select>
        </el-form-item>
        <el-form-item
          ><el-button type="primary" :loading="submitting" @click="submit"
            >保存档案</el-button
          ></el-form-item
        >
      </el-form>
      <p v-if="message" class="create-panel__message" role="status">
        {{ message }}
      </p>
    </section>

    <loading-state v-if="loading" text="正在整理宠物档案…" />
    <error-state
      v-else-if="error"
      title="宠物档案暂时没有加载出来"
      :description="error"
      @retry="loadPets"
    />
    <empty-state
      v-else-if="!pets.length"
      title="先添加一只小伙伴，开始记录它的生活"
      description="宠物档案会连接健康记录、服务预约与日常照护。"
      :image="emptyPetImage"
      data-testid="my-pet-empty"
    />
    <section v-else class="pet-grid" data-testid="my-pet-list">
      <pet-card
        v-for="pet in pets"
        :key="pet.id"
        :pet="pet"
        status="我的伙伴"
        :detail-to="`/my/pets/${pet.id}/health`"
        :data-testid="`my-pet-${pet.id}`"
      >
        <template #actions>
          <router-link
            class="health-link"
            :to="`/my/pets/${pet.id}/health`"
            :data-testid="`pet-health-link-${pet.id}`"
            >查看健康记录</router-link
          >
        </template>
      </pet-card>
    </section>
  </main>
</template>

<script>
import { createMyPet, listPets } from '@/api/pets'
import PageHeader from '@/components/ui/PageHeader.vue'
import LoadingState from '@/components/ui/LoadingState.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import PetCard from '@/components/pet/PetCard.vue'
import emptyPetImage from '@/assets/illustrations/empty-pet.png'

export default {
  name: 'MyPetsView',
  components: { PageHeader, LoadingState, EmptyState, ErrorState, PetCard },
  data() {
    return {
      form: { name: '', species: 'DOG' },
      message: '',
      pets: [],
      loading: false,
      submitting: false,
      error: '',
      page: { page: 1, size: 20 },
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
        const response = await listPets({
          page: this.page.page,
          size: this.page.size,
        })
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
    async submit() {
      if (!this.form.name.trim()) {
        this.message = '请先填写宠物名称'
        return
      }
      this.submitting = true
      this.message = ''
      try {
        await createMyPet(this.form)
        this.message = '宠物档案已保存'
        this.form = { name: '', species: 'DOG' }
        await this.loadPets()
      } catch (error) {
        this.message =
          error.response?.data?.message || error.message || '宠物档案保存失败'
      } finally {
        this.submitting = false
      }
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
.create-panel {
  display: grid;
  grid-template-columns: minmax(190px, 0.65fr) 1.4fr;
  gap: 20px 36px;
  align-items: center;
  padding: 22px 24px;
  margin-bottom: 28px;
  background: linear-gradient(120deg, var(--ps-color-cream), #fff);
  border: 1px solid #f3dfd3;
  border-radius: var(--ps-radius-lg);
}
.create-panel h2 {
  margin: 2px 0 0;
  font-size: 19px;
}
.create-panel__eyebrow {
  margin: 0;
  color: var(--ps-color-peach);
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.08em;
}
.create-panel__form {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
}
.create-panel__form :deep(.el-form-item) {
  margin-bottom: 0;
}
.create-panel__message {
  grid-column: 1 / -1;
  margin: -4px 0 0;
  color: var(--ps-color-green);
  font-size: 13px;
}
.pet-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 22px;
}
.health-link {
  color: var(--ps-color-pink);
  font-weight: 700;
  text-decoration: none;
}
@media (max-width: 760px) {
  .member-page {
    padding: 24px 16px 40px;
  }
  .create-panel {
    grid-template-columns: 1fr;
  }
  .create-panel__form {
    display: grid;
    gap: 12px;
    justify-content: stretch;
  }
  .create-panel__form :deep(.el-form-item),
  .create-panel__form :deep(.el-input),
  .create-panel__form :deep(.el-select) {
    width: 100%;
    margin-right: 0;
  }
}
</style>
