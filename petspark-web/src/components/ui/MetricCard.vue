<template>
  <article class="ps-metric-card">
    <div class="ps-metric-card__label">{{ label }}</div>
    <div class="ps-metric-card__value">{{ value }}</div>
    <div v-if="hint" class="ps-metric-card__hint">{{ hint }}</div>
  </article>
</template>

<script>
export default {
  name: 'MetricCard',
  props: {
    label: { type: String, required: true },
    value: { type: [String, Number], required: true },
    hint: { type: String, default: '' }
  }
}
</script>
