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
          <details class="nav-menu">
            <summary>功能</summary>
            <div class="nav-menu__panel">
              <router-link
                v-for="entry in memberNav"
                :key="entry.to"
                :to="routeTarget(entry.to)"
                :data-testid="entry.dataTestId"
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
          </details>
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
  methods: {
    routeTarget(to) {
      return typeof to === 'string' && to.startsWith('/') ? to : { name: to }
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
.nav-menu summary,
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
.public-nav > a.router-link-exact-active,
.nav-menu summary:hover,
.nav-button:hover {
  color: var(--ps-color-pink-dark);
  background: #fdeef3;
}

.public-nav .nav-register {
  color: #fff;
  background: var(--ps-color-pink);
}

.public-nav .nav-register:hover { color: #fff; background: var(--ps-color-pink-dark); }
.nav-menu { position: relative; }
.nav-menu summary { list-style: none; }
.nav-menu summary::-webkit-details-marker { display: none; }
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
</style>
