<template>
  <section class="home-view">
    <PageHero
      label="PetSpark 派宠"
      title="让每一次陪伴，都被认真照顾"
      description="用 AI、健康档案、寄养预约和领养服务，把宠物生活管理得更安心。"
      primary-text="开始管理我的宠物"
      primary-to="/my/pets"
      secondary-text="看看可领养宠物"
      secondary-to="/adoptions"
      :image-src="heroPets"
      image-alt="猫咪和狗狗在明亮温暖的家中陪伴主人"
    />

    <el-carousel
      v-if="banners.length"
      class="home-banners"
      height="260px"
      data-testid="home-banner-carousel"
    >
      <el-carousel-item v-for="banner in banners" :key="banner.id">
        <a class="home-banner" :href="banner.targetUrl || '#'" @click="handleBannerClick($event, banner)">
          <img :src="banner.imageUrl" :alt="banner.title">
          <span class="home-banner__copy">
            <strong>{{ banner.title }}</strong>
            <small v-if="banner.subtitle">{{ banner.subtitle }}</small>
          </span>
        </a>
      </el-carousel-item>
    </el-carousel>

    <ErrorState
      v-if="error"
      class="home-banner-error"
      title="推荐内容暂时未加载"
      :description="error"
      action-text="重新加载"
      @retry="loadBanners"
    />

    <section class="home-section" aria-labelledby="home-features-title">
      <div class="home-section__head">
        <div>
          <h2 id="home-features-title">今天想为它做什么？</h2>
          <p>从一件具体的小事开始，照顾会变得更轻松。</p>
        </div>
      </div>
      <div class="feature-grid">
        <FeatureCard
          v-for="feature in features"
          :key="feature.title"
          v-bind="feature"
        />
      </div>
    </section>

    <section class="home-ai">
      <div>
        <span class="home-ai__mark" aria-hidden="true"><AppIcon name="ai" /></span>
        <h2>遇到护理问题，先问问智能宠物伙伴</h2>
        <p>提供日常护理信息、服务选择参考和陪伴对话。健康相关回答仅作信息参考，不替代兽医诊断。</p>
      </div>
      <router-link to="/ai/chat">打开 AI 助手</router-link>
    </section>

    <section class="home-trust" aria-label="平台使用原则">
      <div><strong>隐私优先</strong><span>只收集服务所需信息</span></div>
      <div><strong>过程透明</strong><span>预约、申请和订单状态可追踪</span></div>
      <div><strong>AI 有边界</strong><span>重要健康问题请联系专业兽医</span></div>
    </section>
  </section>
</template>

<script>
import { listActiveBanners } from '@/api/banner'
import ErrorState from '@/components/ui/ErrorState.vue'
import PageHero from '@/components/ui/PageHero.vue'
import FeatureCard from '@/components/ui/FeatureCard.vue'
import AppIcon from '@/components/ui/AppIcon.vue'
import heroPets from '@/assets/illustrations/hero-pets.jpg'

export default {
  name: 'HomeView',
  components: { ErrorState, PageHero, FeatureCard, AppIcon },
  data() {
    return {
      heroPets,
      banners: [],
      error: '',
      features: [
        { title: '我的宠物', description: '管理档案与健康记录', icon: 'pets', to: '/my/pets', tone: 'pink' },
        { title: 'AI 助手', description: '获得日常护理信息参考', icon: 'ai', to: '/ai/chat', tone: 'purple' },
        { title: '领养服务', description: '认识正在等待家庭的小伙伴', icon: 'adoption', to: '/adoptions', tone: 'peach' },
        { title: '寄养预约', description: '查询房间并安排照护', icon: 'boarding', to: '/boarding/new', tone: 'green' },
        { title: '宠物服务', description: '训练、美容与线下医疗预约', icon: 'service', to: '/services', tone: 'green' },
        { title: '宠物商城', description: '挑选日常用品并管理订单', icon: 'goods', to: '/goods', tone: 'peach' }
      ]
    }
  },
  created() {
    this.loadBanners()
  },
  methods: {
    async loadBanners() {
      try {
        const response = await listActiveBanners({ limit: 5 })
        this.banners = Array.isArray(response.data) ? response.data : []
        this.error = ''
      } catch (error) {
        this.error = error.message
      }
    },
    handleBannerClick(event, banner) {
      if (!banner.targetUrl) {
        event.preventDefault()
        return
      }
      if (banner.targetUrl.startsWith('/')) {
        event.preventDefault()
        this.$router.push(banner.targetUrl)
      }
    }
  }
}
</script>

<style scoped>
.home-view { display: grid; gap: 34px; }
.home-banners {
  overflow: hidden;
  margin: 0;
  background: var(--ps-color-surface-soft);
  border-radius: var(--ps-radius-lg);
}
.home-banner { position: relative; display: block; height: 100%; color: #fff; text-decoration: none; }
.home-banner img { width: 100%; height: 100%; object-fit: cover; }
.home-banner::after {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(90deg, rgba(18, 27, 36, 0.72), rgba(18, 27, 36, 0.05));
}
.home-banner__copy {
  position: absolute;
  bottom: 38px;
  left: 38px;
  z-index: 1;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.home-banner__copy strong { font-size: 28px; }
.home-banner__copy small { font-size: 15px; }
.home-banner-error { min-height: 150px; }
.home-section { padding: 8px 0; }
.home-section__head { display: flex; justify-content: space-between; gap: 24px; margin-bottom: 20px; }
.home-section__head h2, .home-ai h2 { margin: 0; font-size: 28px; line-height: 1.3; }
.home-section__head p { margin: 6px 0 0; color: var(--ps-color-muted); }
.feature-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px; }
.home-ai {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 32px;
  padding: 32px 36px;
  color: #fff;
  background: #3a315f;
  border-radius: var(--ps-radius-lg);
}
.home-ai > div { display: grid; grid-template-columns: auto 1fr; column-gap: 16px; align-items: center; }
.home-ai__mark {
  display: grid;
  grid-row: span 2;
  width: 52px;
  height: 52px;
  place-items: center;
  color: #3a315f;
  background: #fff;
  border-radius: 14px;
  font-size: 25px;
}
.home-ai p { max-width: 62ch; margin: 7px 0 0; color: #ddd7f5; }
.home-ai a {
  flex: 0 0 auto;
  padding: 10px 16px;
  color: #3a315f;
  background: #fff;
  border-radius: var(--ps-radius-sm);
  text-decoration: none;
  font-weight: 750;
}
.home-trust {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  padding: 22px 0 8px;
  border-top: 1px solid var(--ps-color-border);
}
.home-trust div { display: flex; flex-direction: column; gap: 3px; padding: 0 24px; }
.home-trust div + div { border-left: 1px solid var(--ps-color-border); }
.home-trust span { color: var(--ps-color-muted); font-size: 13px; }
@media (max-width: 900px) {
  .feature-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .home-ai { align-items: flex-start; flex-direction: column; }
}
@media (max-width: 600px) {
  .home-view { gap: 26px; }
  .feature-grid, .home-trust { grid-template-columns: 1fr; }
  .home-ai { padding: 28px 24px; }
  .home-ai > div { grid-template-columns: 1fr; }
  .home-ai__mark { grid-row: auto; margin-bottom: 16px; }
  .home-ai a { width: 100%; text-align: center; }
  .home-trust { gap: 16px; }
  .home-trust div { padding: 0; }
  .home-trust div + div { padding-top: 16px; border-top: 1px solid var(--ps-color-border); border-left: 0; }
}
</style>
