<template>
  <header class="ps-admin-page-header">
    <div class="ps-admin-page-header__copy">
      <span v-if="eyebrow" class="ps-admin-page-header__eyebrow">{{ eyebrow }}</span>
      <h1>{{ title }}</h1>
      <p v-if="description">{{ description }}</p>
    </div>
    <div v-if="$slots.actions" class="ps-admin-page-header__actions">
      <slot name="actions" />
    </div>
  </header>
</template>

<script>
export default {
  name: 'AdminPageHeader',
  props: {
    title: { type: String, required: true },
    description: { type: String, default: '' },
    eyebrow: { type: String, default: '' }
  }
}
</script>

<style scoped>
.ps-admin-page-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  padding-bottom: 18px;
  border-bottom: 1px solid var(--ps-color-border);
}

.ps-admin-page-header__copy {
  min-width: 0;
}

.ps-admin-page-header__eyebrow {
  display: block;
  margin-bottom: 6px;
  color: var(--ps-color-blue);
  font-size: 13px;
  font-weight: 700;
}

h1 {
  margin: 0;
  color: var(--ps-color-text);
  font-size: 24px;
  line-height: 1.3;
  letter-spacing: -0.02em;
  text-wrap: balance;
}

p {
  max-width: 70ch;
  margin: 7px 0 0;
  color: var(--ps-color-muted);
  line-height: 1.6;
  text-wrap: pretty;
}

.ps-admin-page-header__actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 10px;
}

@media (max-width: 720px) {
  .ps-admin-page-header {
    align-items: stretch;
    flex-direction: column;
  }

  .ps-admin-page-header__actions {
    flex-wrap: wrap;
  }
}
</style>
