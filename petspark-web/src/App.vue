<template>
  <div id="app">
    <header class="app-header">
      <div>
        <h1 data-testid="app-title">PetSpark</h1>
        <p>派宠 · 智慧 AI 宠物管理平台</p>
      </div>
      <nav class="app-nav">
        <span v-for="entry in publicNav" :key="entry.to">
          <router-link :to="entry.to">{{ entry.text }}</router-link>
        </span>
        <template v-if="isAuthenticated">
          <router-link
            v-for="entry in memberNav"
            :key="entry.to"
            :to="entry.to"
            :data-testid="entry.dataTestId">
            {{ entry.text }}
            <span
              v-if="entry.badge === 'notifications' && notificationUnreadCount > 0"
              class="nav-badge"
              data-testid="nav-notifications-badge"
            >{{ notificationUnreadCountText }}</span>
          </router-link
          >
          <router-link
            v-for="entry in adminNav"
            :key="entry.to"
            :to="entry.to"
            :data-testid="entry.dataTestId">{{ entry.text }}</router-link
          >
          <span>{{ userNickname }}</span>
          <button type="button" class="nav-button" @click="signOut">退出</button>
        </template>
        <template v-else>
          <router-link to="/login">登录</router-link>
          <router-link to="/register">注册</router-link>
        </template>
      </nav>
    </header>
    <main><router-view /></main>
  </div>
</template>

<script>
import { logout } from '@/api/auth'
import navigation from '@/navigation'

export default {
  name: 'App',
  data() {
    return {
      publicNav: navigation.publicNav,
      memberNav: navigation.memberNav,
      adminNav: navigation.adminNav,
      notificationCountTimer: null
    }
  },
  computed: {
    isAuthenticated() {
      return Boolean(this.$store && this.$store.getters && this.$store.getters.isAuthenticated)
    },
    userNickname() {
      return this.$store && this.$store.state && this.$store.state.user
        ? this.$store.state.user.nickname
        : ''
    },
    notificationUnreadCount() {
      return this.$store && this.$store.state ? this.$store.state.notificationUnreadCount || 0 : 0
    },
    notificationUnreadCountText() {
      return this.notificationUnreadCount > 99 ? '99+' : String(this.notificationUnreadCount)
    }
  },
  created() {
    this.startNotificationCountPolling()
  },
  beforeDestroy() {
    this.stopNotificationCountPolling()
  },
  watch: {
    isAuthenticated(value) {
      if (value) {
        this.refreshNotificationCount()
        this.startNotificationCountPolling()
      } else {
        this.stopNotificationCountPolling()
        this.$store.dispatch('setNotificationUnreadCount', 0)
      }
    }
  },
  methods: {
    refreshNotificationCount() {
      if (!this.isAuthenticated || !this.$store || !this.$store.dispatch) return
      this.$store.dispatch('refreshNotificationUnreadCount').catch(() => {})
    },
    startNotificationCountPolling() {
      if (!this.isAuthenticated || this.notificationCountTimer) return
      this.refreshNotificationCount()
      this.notificationCountTimer = window.setInterval(this.refreshNotificationCount, 60000)
    },
    stopNotificationCountPolling() {
      if (this.notificationCountTimer) {
        window.clearInterval(this.notificationCountTimer)
        this.notificationCountTimer = null
      }
    },
    async signOut() {
      try {
        await logout()
      } finally {
        await this.$store.dispatch('logout')
        if (this.$route.path !== '/login') this.$router.push('/login')
      }
    }
  }
}
</script>

<style>
body { margin: 0; color: #24313d; background: #f5f7fa; font-family: "Microsoft YaHei", Arial, sans-serif; }
.app-header { padding: 24px; color: #fff; background: #409eff; display: flex; justify-content: space-between; align-items: center; gap: 24px; }
.app-header h1, .app-header p { margin: 0; }
.app-header p { margin-top: 8px; }
.app-nav { display: flex; gap: 16px; align-items: center; }
.app-nav a { color: #fff; text-decoration: none; font-weight: 600; position: relative; display: inline-flex; align-items: center; gap: 4px; }
.app-nav a.router-link-exact-active { text-decoration: underline; }
.nav-badge { min-width: 16px; height: 16px; padding: 0 4px; border-radius: 999px; color: #f56c6c; background: #fff; font-size: 11px; line-height: 16px; text-align: center; }
.nav-button { padding: 0; color: #fff; background: transparent; border: 0; font: inherit; font-weight: 600; cursor: pointer; }
</style>
