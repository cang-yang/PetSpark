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

    <el-alert v-if="error" :title="error" type="warning" show-icon :closable="false" />

    <div class="home-card">
      <h2>欢迎来到 PetSpark</h2>
      <p>发现商品、预约服务、管理宠物健康，用一站式平台守护爱宠生活。</p>
    </div>
  </section>
</template>

<script>
import { listActiveBanners } from '@/api/banner'

export default {
  name: 'HomeView',
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
</style>
