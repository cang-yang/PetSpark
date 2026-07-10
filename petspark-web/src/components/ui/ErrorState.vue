<template>
  <section class="ps-state ps-error-state" role="alert">
    <div class="ps-state__content">
      <div class="ps-state__icon" aria-hidden="true">!</div>
      <h2>{{ title }}</h2>
      <p>{{ description }}</p>
      <div class="ps-state__action">
        <el-button
          type="primary"
          data-testid="error-state-retry"
          @click="$emit('retry')"
        >{{ actionText }}</el-button>
      </div>
    </div>
  </section>
</template>

<script>
export default {
  name: 'ErrorState',
  props: {
    title: { type: String, default: '页面暂时不可用' },
    description: { type: String, default: '请检查网络连接后重试。' },
    actionText: { type: String, default: '重新加载' }
  }
}
</script>
