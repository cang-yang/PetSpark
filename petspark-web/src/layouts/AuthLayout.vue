<template>
  <div class="ps-auth-shell">
    <PageAtmosphere scene="care" />
    <header class="ps-auth-header">
      <router-link class="ps-brand" to="/" aria-label="返回 PetSpark 首页">
        <span class="ps-brand-mark" aria-hidden="true">派</span>
        <span class="ps-brand-copy">PetSpark<small>认真照顾每一次陪伴</small></span>
      </router-link>
      <nav class="ps-auth-nav" aria-label="认证导航">
        <router-link to="/">返回首页</router-link>
      </nav>
    </header>
    <main class="ps-auth-main"><slot /></main>
  </div>
</template>

<script>
import PageAtmosphere from '@/components/ui/PageAtmosphere.vue'

export default {
  name: 'AuthLayout',
  components: { PageAtmosphere }
}
</script>

<style scoped>
.ps-auth-nav a {
  color: var(--ps-color-muted);
  text-decoration: none;
  font-weight: 600;
}
.ps-auth-nav a:hover { color: var(--ps-color-pink-dark); }
</style>
