<template>
  <div id="app">
    <header class="app-header">
      <h1 data-testid="app-title">PetSpark</h1>
      <p>派宠 · 智慧宠物管理平台</p>
    </header>
    <main>
      <router-view />
    </main>
  </div>
</template>

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
}

.app-header h1,
.app-header p {
  margin: 0;
}

.app-header p {
  margin-top: 8px;
}
</style>
