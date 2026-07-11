<template>
  <main class="pet-profile-page">
    <loading-state v-if="loading" text="正在整理宠物档案…" />
    <error-state
      v-else-if="error"
      title="这份宠物档案暂时无法打开"
      :description="error"
      @retry="load"
    />
    <template v-else-if="pet">
      <section class="profile-hero">
        <div class="profile-gallery">
          <pet-avatar :pet="pet" :src="activeImage" shape="cover" />
          <div v-if="pet.images && pet.images.length > 1" class="profile-gallery__thumbs">
            <button
              v-for="image in pet.images"
              :key="image.fileId"
              type="button"
              :class="{ 'is-active': activeImage === image.previewUrl }"
              :aria-label="`查看${pet.name}的另一张照片`"
              @click="activeImage = image.previewUrl"
            >
              <img :src="image.previewUrl" alt="" />
            </button>
          </div>
        </div>

        <div class="profile-hero__content">
          <p class="profile-eyebrow">{{ speciesLabel(pet.species) }} · {{ pet.breedName || '品种待补充' }}</p>
          <div class="profile-title-row">
            <h1>{{ pet.name }}</h1>
            <span class="profile-status">{{ adoptionLabel(pet.adoptionStatus) }}</span>
          </div>
          <p class="profile-lead">{{ pet.description || '它正在等待一位愿意认真了解它的人。' }}</p>
          <div class="profile-facts" aria-label="宠物基本资料">
            <span><strong>性别</strong>{{ sexLabel(pet.sex) }}</span>
            <span><strong>年龄</strong>{{ ageLabel(pet.birthDate) }}</span>
            <span><strong>毛色</strong>{{ pet.color || '待补充' }}</span>
            <span><strong>登记</strong>{{ pet.registeredAt || '待补充' }}</span>
          </div>
          <div class="profile-actions">
            <el-button
              v-if="pet.adoptionStatus === 'AVAILABLE' && !pet.ownedByCurrentUser"
              type="primary"
              data-testid="pet-adoption-cta"
              @click="openAdoption"
            >申请认识它</el-button>
            <router-link
              v-if="pet.ownedByCurrentUser"
              class="profile-health-link"
              :to="`/my/pets/${pet.id}/health`"
              data-testid="pet-health-cta"
            >查看健康档案</router-link>
            <router-link class="profile-back-link" to="/pets">返回宠物目录</router-link>
          </div>
        </div>
      </section>

      <section class="profile-sections">
        <article class="profile-panel profile-panel--story">
          <p class="profile-eyebrow">相处指南</p>
          <h2>认识 {{ pet.name }} 的性格</h2>
          <p>{{ pet.behaviorTraits || '性格资料正在由照护人员持续补充。' }}</p>
        </article>
        <article class="profile-panel">
          <p class="profile-eyebrow">生活资料</p>
          <h2>照护信息</h2>
          <dl>
            <div><dt>绝育情况</dt><dd>{{ sterilizationLabel(pet.sterilizationStatus) }}</dd></div>
            <div><dt>训练程度</dt><dd>{{ trainingLabel(pet.trainingLevel) }}</dd></div>
          </dl>
        </article>
        <article class="profile-panel profile-panel--needs">
          <p class="profile-eyebrow">请特别留意</p>
          <h2>特殊照护需求</h2>
          <p>{{ pet.specialNeeds || '目前没有额外的特殊照护要求。' }}</p>
        </article>
      </section>
    </template>
  </main>
</template>

<script>
import { getPet } from '@/api/pets'
import PetAvatar from '@/components/pet/PetAvatar.vue'
import LoadingState from '@/components/ui/LoadingState.vue'
import ErrorState from '@/components/ui/ErrorState.vue'

export default {
  name: 'PetDetailView',
  components: { PetAvatar, LoadingState, ErrorState },
  data() {
    return { pet: null, activeImage: '', loading: false, error: '' }
  },
  created() { this.load() },
  methods: {
    async load() {
      this.loading = true
      this.error = ''
      try {
        const response = await getPet(this.$route.params.id)
        this.pet = response.data
        const cover = (this.pet.images || []).find((image) => image.coverFlag) || (this.pet.images || [])[0]
        this.activeImage = cover ? cover.previewUrl : ''
      } catch (error) {
        this.error = error.message || '请稍后重试。'
      } finally {
        this.loading = false
      }
    },
    openAdoption() {
      this.$router.push({ name: 'adoptions', query: { petId: this.pet.id } })
    },
    speciesLabel(value) { return ({ DOG: '犬类伙伴', CAT: '猫咪伙伴', RABBIT: '兔兔伙伴', BIRD: '鸟类伙伴' }[value] || '宠物伙伴') },
    sexLabel(value) { return ({ MALE: '男孩', FEMALE: '女孩', UNKNOWN: '待确认' }[value] || '待确认') },
    adoptionLabel(value) { return ({ AVAILABLE: '等待领养', ADOPTING: '领养沟通中', ADOPTED: '已有归属', NOT_FOR_ADOPTION: '非领养状态' }[value] || '档案已登记') },
    sterilizationLabel(value) { return ({ STERILIZED: '已绝育', INTACT: '未绝育', UNKNOWN: '待确认' }[value] || '待确认') },
    trainingLabel(value) { return ({ BASIC: '基础训练', INTERMEDIATE: '进阶训练', ADVANCED: '训练良好', UNASSESSED: '待评估' }[value] || '待评估') },
    ageLabel(value) {
      if (!value) return '年龄待补充'
      const birth = new Date(value)
      if (Number.isNaN(birth.getTime())) return '年龄待补充'
      const today = new Date()
      let months = (today.getFullYear() - birth.getFullYear()) * 12 + today.getMonth() - birth.getMonth()
      if (today.getDate() < birth.getDate()) months -= 1
      months = Math.max(0, months)
      return months < 12 ? `${months || 1} 个月` : `${Math.floor(months / 12)} 岁${months % 12 ? ` ${months % 12} 个月` : ''}`
    }
  }
}
</script>

<style scoped>
.pet-profile-page { width: min(1180px, 100%); min-height: calc(100vh - 80px); margin: 0 auto; padding: 42px 24px 72px; }
.profile-hero { display: grid; grid-template-columns: minmax(0, 1.05fr) minmax(360px, .95fr); overflow: hidden; background: rgba(255,255,255,.94); border: 1px solid var(--ps-color-border); border-radius: 30px; box-shadow: var(--ps-shadow-float); }
.profile-gallery { min-width: 0; padding: 20px; background: linear-gradient(145deg, #fff3ed, #f4f0ff); }
.profile-gallery :deep(.pet-avatar--cover) { aspect-ratio: 4 / 3; border-radius: 22px; }
.profile-gallery__thumbs { display: flex; gap: 10px; margin-top: 12px; }
.profile-gallery__thumbs button { width: 68px; height: 52px; padding: 0; overflow: hidden; border: 2px solid transparent; border-radius: 12px; background: white; cursor: pointer; }
.profile-gallery__thumbs button.is-active { border-color: var(--ps-color-pink); }
.profile-gallery__thumbs img { width: 100%; height: 100%; object-fit: cover; }
.profile-hero__content { display: flex; flex-direction: column; justify-content: center; padding: 48px 44px; }
.profile-eyebrow { margin: 0 0 8px; color: var(--ps-color-pink); font-size: 13px; font-weight: 800; letter-spacing: .08em; }
.profile-title-row { display: flex; gap: 16px; align-items: center; }
.profile-title-row h1 { margin: 0; color: var(--ps-color-text); font-size: clamp(38px, 5vw, 62px); line-height: 1; }
.profile-status { padding: 7px 12px; color: var(--ps-color-green); background: rgba(79,156,99,.1); border-radius: 999px; font-size: 13px; font-weight: 800; }
.profile-lead { margin: 24px 0; color: var(--ps-color-muted); font-size: 17px; line-height: 1.8; }
.profile-facts { display: grid; grid-template-columns: repeat(2, 1fr); gap: 12px; }
.profile-facts span { padding: 13px 15px; color: var(--ps-color-text); background: var(--ps-color-cream); border-radius: 14px; }
.profile-facts strong { display: block; margin-bottom: 4px; color: var(--ps-color-muted); font-size: 11px; }
.profile-actions { display: flex; flex-wrap: wrap; gap: 12px; align-items: center; margin-top: 28px; }
.profile-health-link, .profile-back-link { padding: 11px 16px; border-radius: 12px; font-weight: 800; text-decoration: none; }
.profile-health-link { color: white; background: var(--ps-color-pink); }
.profile-back-link { color: var(--ps-color-muted); }
.profile-sections { display: grid; grid-template-columns: 1.25fr .75fr; gap: 20px; margin-top: 22px; }
.profile-panel { padding: 28px; background: rgba(255,255,255,.92); border: 1px solid var(--ps-color-border); border-radius: 22px; box-shadow: var(--ps-shadow-card); }
.profile-panel--story { grid-row: span 2; }
.profile-panel h2 { margin: 0 0 14px; font-size: 22px; }
.profile-panel p:last-child { margin: 0; color: var(--ps-color-muted); line-height: 1.8; white-space: pre-line; }
.profile-panel dl { margin: 0; }
.profile-panel dl div { display: flex; justify-content: space-between; padding: 10px 0; border-top: 1px solid var(--ps-color-border); }
.profile-panel dt { color: var(--ps-color-muted); }
.profile-panel dd { margin: 0; font-weight: 800; }
.profile-panel--needs { background: linear-gradient(135deg, rgba(255,245,238,.96), rgba(255,255,255,.96)); }
@media (max-width: 820px) { .profile-hero, .profile-sections { grid-template-columns: 1fr; } .profile-hero__content { padding: 30px 24px; } .profile-panel--story { grid-row: auto; } }
@media (max-width: 520px) { .pet-profile-page { padding: 24px 14px 48px; } .profile-facts { grid-template-columns: 1fr; } .profile-title-row { align-items: flex-start; flex-direction: column; } }
</style>
