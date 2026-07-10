<template>
  <section class="ps-state ps-loading-state" aria-busy="true" aria-live="polite">
    <div class="ps-state__content">
      <span class="ps-loading-state__dot" aria-hidden="true" />
      <p>{{ text }}</p>
    </div>
  </section>
</template>

<script>
export default {
  name: 'LoadingState',
  props: {
    text: { type: String, default: '正在准备内容…' }
  }
}
</script>

<style scoped>
.ps-loading-state__dot {
  display: block;
  width: 24px;
  height: 24px;
  margin: 0 auto 12px;
  background: var(--ps-color-pink);
  border-radius: 50%;
  animation: ps-pulse-soft 1.1s ease-in-out infinite;
}
</style>
