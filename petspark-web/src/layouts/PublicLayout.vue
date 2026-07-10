<template>
  <div class="ps-public-shell">
    <header class="ps-public-header">
      <router-link class="ps-brand" to="/" aria-label="PetSpark 首页">
        <BrandMark tagline="智慧宠物生活平台" />
      </router-link>

      <nav class="public-nav" aria-label="主导航">
        <router-link
          v-for="entry in publicNav"
          :key="entry.to"
          :to="routeTarget(entry.to)"
          :data-testid="entry.dataTestId"
        ><AppIcon :name="entry.icon" />{{ entry.text }}</router-link>

        <template v-if="isAuthenticated">
          <div ref="navMenu" class="nav-menu">
            <button
              type="button"
              ref="navMenuTrigger"
              class="nav-menu__trigger"
              :aria-expanded="String(navMenuOpen)"
              aria-controls="member-navigation-panel"
              data-testid="nav-menu-trigger"
              @click.stop="toggleNavMenu"
              @keydown.esc.stop.prevent="closeNavMenu"
            >功能</button>
            <transition name="nav-menu-fade">
              <div id="member-navigation-panel" v-show="navMenuOpen" class="nav-menu__panel" data-testid="nav-menu-panel">
              <router-link
                v-for="entry in memberNav"
                :key="entry.to"
                :to="routeTarget(entry.to)"
                :data-testid="entry.dataTestId"
                @click.native="closeNavMenu"
              >
                <AppIcon :name="entry.icon" />
                {{ entry.text }}
                <span
                  v-if="entry.badge === 'notifications' && notificationUnreadCount > 0"
                  class="nav-badge"
                  data-testid="nav-notifications-badge"
                >{{ notificationUnreadCountText }}</span>
              </router-link>
              </div>
            </transition>
          </div>
          <router-link v-if="adminNav.length" :to="routeTarget(adminNav[0].to)">管理端</router-link>
          <span class="user-pill">{{ userNickname || '已登录' }}</span>
          <button type="button" class="nav-button" @click="$emit('sign-out')">退出</button>
        </template>
        <template v-else>
          <router-link to="/login">登录</router-link>
          <router-link class="nav-register" to="/register">注册</router-link>
        </template>
      </nav>
    </header>
    <main class="ps-public-main"><slot /></main>
    <AiAssistantBubble v-if="showAiAssistant" />
  </div>
</template>

<script>
import AiAssistantBubble from '@/components/ui/AiAssistantBubble.vue'
import AppIcon from '@/components/ui/AppIcon.vue'
import BrandMark from '@/components/ui/BrandMark.vue'

export default {
  name: 'PublicLayout',
  components: { AiAssistantBubble, AppIcon, BrandMark },
  props: {
    publicNav: { type: Array, default: () => [] },
    memberNav: { type: Array, default: () => [] },
    adminNav: { type: Array, default: () => [] },
    isAuthenticated: { type: Boolean, default: false },
    userNickname: { type: String, default: '' },
    notificationUnreadCount: { type: Number, default: 0 },
    notificationUnreadCountText: { type: String, default: '0' },
    showAiAssistant: { type: Boolean, default: true }
  },
  data() {
    return { navMenuOpen: false }
  },
  watch: {
    '$route.fullPath'() {
      this.closeNavMenu()
    },
    isAuthenticated(value) {
      if (!value) this.closeNavMenu()
    }
  },
  mounted() {
    document.addEventListener('pointerdown', this.handleDocumentPointerDown)
    document.addEventListener('keydown', this.handleDocumentKeydown)
  },
  beforeDestroy() {
    document.removeEventListener('pointerdown', this.handleDocumentPointerDown)
    document.removeEventListener('keydown', this.handleDocumentKeydown)
  },
  methods: {
    routeTarget(to) {
      return typeof to === 'string' && to.startsWith('/') ? to : { name: to }
    },
    toggleNavMenu() {
      this.navMenuOpen = !this.navMenuOpen
    },
    closeNavMenu() {
      this.navMenuOpen = false
    },
    handleDocumentPointerDown(event) {
      if (!this.navMenuOpen) return
      const menu = this.$refs.navMenu
      if (!menu || !menu.contains(event.target)) this.closeNavMenu()
    },
    handleDocumentKeydown(event) {
      if (event.key !== 'Escape' || !this.navMenuOpen) return
      this.closeNavMenu()
      this.$nextTick(() => {
        if (this.$refs.navMenuTrigger) this.$refs.navMenuTrigger.focus()
      })
    }
  }
}
</script>

<style scoped>
.public-nav {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  margin-left: auto;
}

.public-nav > a,
.nav-menu__trigger,
.nav-button {
  padding: 8px 10px;
  color: var(--ps-color-muted);
  background: transparent;
  border: 0;
  border-radius: var(--ps-radius-sm);
  text-decoration: none;
  font-weight: 650;
  cursor: pointer;
}
.public-nav > a { display: inline-flex; align-items: center; gap: 6px; }

.public-nav > a:hover,
.nav-menu__trigger:hover,
.nav-button:hover {
  color: var(--ps-color-pink-dark);
  background: #fdeef3;
}

.public-nav > a.router-link-exact-active {
  position: relative;
  color: var(--ps-color-pink-dark);
  background: transparent;
}
.public-nav > a.router-link-exact-active::after {
  content: '';
  position: absolute;
  right: 10px;
  bottom: 3px;
  left: 10px;
  height: 2px;
  background: currentColor;
  border-radius: 2px;
}
.public-nav > a:focus:not(:focus-visible),
.nav-menu__trigger:focus:not(:focus-visible),
.nav-button:focus:not(:focus-visible) { outline: none; }
.public-nav > a:focus-visible,
.nav-menu__trigger:focus-visible,
.nav-button:focus-visible { outline: 3px solid rgba(233, 52, 114, 0.24); outline-offset: 2px; }

.public-nav .nav-register {
  color: #fff;
  background: var(--ps-color-pink);
}

.public-nav .nav-register:hover { color: #fff; background: var(--ps-color-pink-dark); }
.nav-menu { position: relative; }
.nav-menu__trigger { font: inherit; }
.nav-menu__panel {
  position: absolute;
  top: calc(100% + 10px);
  right: 0;
  z-index: var(--ps-z-dropdown);
  display: grid;
  grid-template-columns: repeat(2, minmax(140px, 1fr));
  width: min(430px, calc(100vw - 32px));
  padding: 10px;
  background: #fff;
  border: 1px solid var(--ps-color-border);
  border-radius: var(--ps-radius-md);
  box-shadow: var(--ps-shadow-float);
  -webkit-backdrop-filter: blur(12px);
  backdrop-filter: blur(12px);
}
.nav-menu__panel a {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: 8px;
  padding: 9px 10px;
  color: var(--ps-color-text);
  border-radius: var(--ps-radius-sm);
  text-decoration: none;
}
.nav-menu__panel .nav-badge { margin-left: auto; }
.nav-menu__panel a:hover { background: var(--ps-color-surface-soft); }
.nav-menu-fade-enter-active,
.nav-menu-fade-leave-active { transition: opacity 140ms ease, transform 140ms ease; transform-origin: top right; }
.nav-menu-fade-enter,
.nav-menu-fade-leave-to { opacity: 0; transform: translateY(-5px) scale(0.98); }
.nav-badge {
  min-width: 20px;
  padding: 1px 6px;
  color: #fff;
  background: var(--ps-color-danger);
  border-radius: var(--ps-radius-pill);
  font-size: 11px;
  text-align: center;
}
.user-pill {
  max-width: 120px;
  overflow: hidden;
  padding: 6px 10px;
  color: var(--ps-color-text);
  background: var(--ps-color-surface-soft);
  border-radius: var(--ps-radius-pill);
  text-overflow: ellipsis;
  white-space: nowrap;
}
@media (max-width: 680px) {
  .public-nav { width: 100%; justify-content: flex-end; }
  .user-pill { display: none; }
  .nav-menu__panel { right: -80px; grid-template-columns: 1fr; max-height: 70vh; overflow-y: auto; }
}
@media (prefers-reduced-motion: reduce) {
  .nav-menu-fade-enter-active,
  .nav-menu-fade-leave-active { transition-duration: 1ms; }
  .nav-menu-fade-enter,
  .nav-menu-fade-leave-to { transform: none; }
}
</style>
