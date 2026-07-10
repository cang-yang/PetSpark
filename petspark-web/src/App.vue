<template>
  <div id="app">
    <span class="ps-sr-only" data-testid="app-title">PetSpark</span>
    <PageAtmosphere :scene="routeScene" />
    <component
      :is="layoutComponent"
      v-bind="layoutProps"
      @sign-out="signOut"
    >
      <transition name="ps-page" mode="out-in">
        <router-view :key="$route.path" />
      </transition>
    </component>
  </div>
</template>

<script>
import { logout } from '@/api/auth'
import navigation from '@/navigation'
import PublicLayout from '@/layouts/PublicLayout.vue'
import AdminLayout from '@/layouts/AdminLayout.vue'
import AuthLayout from '@/layouts/AuthLayout.vue'
import PageAtmosphere from '@/components/ui/PageAtmosphere.vue'
import { inferRouteScene } from '@/ui/scene'

export default {
  name: 'App',
  components: { PublicLayout, AdminLayout, AuthLayout, PageAtmosphere },
  data() {
    return {
      publicNav: navigation.publicNav,
      memberNav: navigation.memberNav,
      adminNav: navigation.adminNav,
      notificationCountTimer: null
    }
  },
  computed: {
    routeLayout() {
      return this.$route && this.$route.meta ? this.$route.meta.layout : 'public'
    },
    layoutComponent() {
      if (this.routeLayout === 'auth') return AuthLayout
      if (this.routeLayout === 'admin') return AdminLayout
      return PublicLayout
    },
    layoutProps() {
      if (this.routeLayout === 'auth') return {}
      if (this.routeLayout === 'admin') {
        return {
          adminNav: this.adminNav,
          userNickname: this.userNickname
        }
      }
      return {
        publicNav: this.publicNav,
        memberNav: this.memberNav,
        adminNav: this.adminNav,
        isAuthenticated: this.isAuthenticated,
        userNickname: this.userNickname,
        notificationUnreadCount: this.notificationUnreadCount,
        notificationUnreadCountText: this.notificationUnreadCountText,
        showAiAssistant: !this.currentPath.startsWith('/ai')
      }
    },
    currentPath() {
      return this.$route && this.$route.path ? this.$route.path : '/'
    },
    routeScene() {
      return inferRouteScene(this.currentPath)
    },
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
