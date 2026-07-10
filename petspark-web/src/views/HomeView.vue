<template>
  <section class="home-view">
    <el-carousel v-if="banners.length" class="home-banners" height="260px" data-testid="home-banner-carousel">
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

    <div class="home-card">
      <h2>欢迎来到 PetSpark</h2>
      <p>发现商品、预约服务、管理宠物健康，用一站式平台守护爱宠生活。</p>
    </div>
  </section>
</template>

<script>
import { listActiveBanners } from '@/api/banner'
import ErrorState from '@/components/ui/ErrorState.vue'

export default {
  name: 'HomeView',
  components: { ErrorState },
  data() {
    return {
      banners: [],
      error: ''
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
.home-view {
  max-width: 1180px;
  margin: 24px auto;
  padding: 0 24px;
}
.home-banners {
  margin-bottom: 24px;
  border-radius: 12px;
  overflow: hidden;
  background: #f5f7fa;
}
.home-banner-error {
  min-height: 160px;
  margin-bottom: 20px;
}
.home-banner-error::v-deep .ps-state__content {
  display: grid;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: 12px;
  max-width: 760px;
  text-align: left;
}
.home-banner-error::v-deep .ps-state__icon,
.home-banner-error::v-deep .ps-state__action {
  margin: 0;
}
.home-banner-error::v-deep h2 {
  font-size: 16px;
}
.home-banner-error::v-deep p {
  margin-top: 2px;
}
.home-banner {
  position: relative;
  display: block;
  height: 100%;
  color: #fff;
  text-decoration: none;
}
.home-banner img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.home-banner::after {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(90deg, rgba(0, 0, 0, 0.45), rgba(0, 0, 0, 0.05));
}
.home-banner__copy {
  position: absolute;
  z-index: 1;
  left: 42px;
  bottom: 42px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.home-banner__copy strong {
  font-size: 28px;
}
.home-banner__copy small {
  font-size: 16px;
}
.home-card {
  padding: 24px;
  background: #fff;
  border-radius: 8px;
}
@media (max-width: 600px) {
  .home-view {
    margin-top: 8px;
    padding: 0;
  }
  .home-banner-error::v-deep .ps-state__content {
    grid-template-columns: 1fr;
    text-align: center;
  }
  .home-banner-error::v-deep .ps-state__icon,
  .home-banner-error::v-deep .ps-state__action {
    margin: 0 auto;
  }
}
</style>
