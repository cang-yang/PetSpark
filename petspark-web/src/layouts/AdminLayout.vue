<template>
  <div class="ps-admin-shell">
    <aside class="ps-admin-sidebar">
      <router-link class="admin-brand" to="/">
        <span class="ps-brand-mark" aria-hidden="true">派</span>
        <span>PetSpark 管理台</span>
      </router-link>
      <nav class="admin-nav" aria-label="管理端导航">
        <router-link
          v-for="entry in adminNav"
          :key="entry.to"
          :to="routeTarget(entry.to)"
          :data-testid="entry.dataTestId"
        >{{ entry.text }}</router-link>
      </nav>
    </aside>
    <section class="ps-admin-content">
      <header class="ps-admin-topbar">
        <div>
          <strong>管理控制台</strong>
          <span class="admin-topbar__hint">清晰处理平台运营事务</span>
        </div>
        <div class="admin-user">
          <router-link to="/">返回用户端</router-link>
          <span>{{ userNickname || '管理员' }}</span>
          <button type="button" @click="$emit('sign-out')">退出</button>
        </div>
      </header>
      <main class="ps-admin-main"><slot /></main>
    </section>
  </div>
</template>

<script>
export default {
  name: 'AdminLayout',
  props: {
    adminNav: { type: Array, default: () => [] },
    userNickname: { type: String, default: '' }
  },
  methods: {
    routeTarget(to) {
      return typeof to === 'string' && to.startsWith('/') ? to : { name: to }
    }
  }
}
</script>

<style scoped>
.admin-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 0 4px 20px;
  color: #fff;
  text-decoration: none;
  font-weight: 800;
}
.admin-nav { display: grid; gap: 3px; }
.admin-nav a {
  padding: 9px 12px;
  color: #c6d1dc;
  border-radius: var(--ps-radius-sm);
  text-decoration: none;
}
.admin-nav a:hover,
.admin-nav a.router-link-active { color: #fff; background: rgba(255, 255, 255, 0.10); }
.admin-topbar__hint { margin-left: 10px; color: var(--ps-color-muted); font-size: 13px; }
.admin-user { display: flex; align-items: center; gap: 14px; }
.admin-user a { color: var(--ps-color-blue); text-decoration: none; }
.admin-user button { padding: 5px 9px; color: var(--ps-color-muted); background: transparent; border: 0; cursor: pointer; }
@media (max-width: 900px) {
  .admin-nav { display: flex; overflow-x: auto; padding-bottom: 4px; }
  .admin-nav a { flex: 0 0 auto; }
}
@media (max-width: 600px) {
  .admin-topbar__hint, .admin-user span { display: none; }
  .ps-admin-topbar { padding: 10px 16px; }
}
</style>
