<template>
  <div id="app">
    <header class="app-header">
      <div>
        <h1 data-testid="app-title">PetSpark</h1>
        <p>派宠 · 智慧 AI 宠物管理平台</p>
      </div>
      <nav class="app-nav">
        <router-link to="/">首页</router-link>
        <template v-if="$store && $store.getters.isAuthenticated">
          <router-link to="/profile">个人资料</router-link>
          <router-link to="/notifications">通知中心</router-link>
          <router-link to="/admin/users">用户管理</router-link>
          <span>{{ $store.state.user && $store.state.user.nickname }}</span>
          <button type="button" class="nav-button" @click="signOut">退出</button>
        </template>
        <template v-else>
          <router-link to="/login">登录</router-link>
          <router-link to="/register">注册</router-link>
        </template>
      </nav>
    </header>
    <main>
      <router-view />
    </main>
  </div>
</template>

<script>
import { logout } from '@/api/auth'

export default {
  name: 'App',
  methods: {
    async signOut() {
      try {
        await logout()
      } finally {
        await this.$store.dispatch('logout')
        if (this.$route.path !== '/login') {
          this.$router.push('/login')
        }
      }
    }
  }
}
</script>

<style>
body {
  margin: 0;
  color: #24313d;
  background: #f5f7fa;
  font-family: "Microsoft YaHei", Arial, sans-serif;
}

.app-header {
  padding: 24px;
  color: #fff;
  background: #409eff;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 24px;
}

.app-header h1,
.app-header p {
  margin: 0;
}

.app-header p {
  margin-top: 8px;
}

.app-nav {
  display: flex;
  gap: 16px;
}

.app-nav a {
  color: #fff;
  text-decoration: none;
  font-weight: 600;
}

.app-nav a.router-link-exact-active {
  text-decoration: underline;
}

.nav-button {
  padding: 0;
  color: #fff;
  background: transparent;
  border: 0;
  font: inherit;
  font-weight: 600;
  cursor: pointer;
}
</style>
