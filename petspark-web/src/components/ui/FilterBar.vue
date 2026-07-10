<template>
  <section class="ps-filter-bar" aria-label="筛选条件">
    <slot />
    <div v-if="$slots.actions" class="ps-filter-bar__actions">
      <slot name="actions" />
    </div>
  </section>
</template>

<script>
export default { name: 'FilterBar' }
</script>
