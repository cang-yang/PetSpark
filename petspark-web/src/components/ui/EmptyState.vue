<template>
  <section class="ps-state ps-empty-state" role="status">
    <div class="ps-state__content">
      <img v-if="image" class="ps-state__image" :src="image" :alt="imageAlt">
      <div v-else class="ps-state__icon" aria-hidden="true">＋</div>
      <h2>{{ title }}</h2>
      <p v-if="description">{{ description }}</p>
      <div v-if="actionText || $slots.action" class="ps-state__action">
        <slot name="action">
          <el-button
            type="primary"
            data-testid="empty-state-action"
            @click="$emit('action')"
          >{{ actionText }}</el-button>
        </slot>
      </div>
    </div>
  </section>
</template>

<script>
export default {
  name: 'EmptyState',
  props: {
    title: { type: String, default: '这里还没有内容' },
    description: { type: String, default: '完成第一步操作后，内容会显示在这里。' },
    actionText: { type: String, default: '' },
    image: { type: String, default: '' },
    imageAlt: { type: String, default: '' }
  }
}
</script>
