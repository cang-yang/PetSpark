<template>
  <section
    class="ps-admin-table-shell"
    data-testid="admin-table-shell"
    :aria-label="title"
  >
    <div v-if="$slots.filters || $slots.actions" class="ps-admin-table-shell__toolbar">
      <div v-if="$slots.filters" class="ps-admin-table-shell__filters">
        <slot name="filters" />
      </div>
      <div v-if="$slots.actions" class="ps-admin-table-shell__actions">
        <slot name="actions" />
      </div>
    </div>

    <header v-if="title || showTotal" class="ps-admin-table-shell__heading">
      <h2 v-if="title">{{ title }}</h2>
      <span v-if="showTotal" class="ps-admin-table-shell__total">共 {{ total }} 条</span>
    </header>

    <div class="ps-admin-table-shell__body">
      <slot />
    </div>

    <footer v-if="$slots.pagination" class="ps-admin-table-shell__footer">
      <slot name="pagination" />
    </footer>
  </section>
</template>

<script>
export default {
  name: 'AdminTableShell',
  props: {
    title: { type: String, default: '数据列表' },
    total: { type: Number, default: 0 },
    showTotal: { type: Boolean, default: true }
  }
}
</script>

<style scoped>
.ps-admin-table-shell {
  overflow: hidden;
  background: var(--ps-color-surface);
  border: 1px solid var(--ps-color-border);
  border-radius: var(--ps-radius-md);
}

.ps-admin-table-shell__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 16px 18px;
  background: var(--ps-color-surface-soft);
  border-bottom: 1px solid var(--ps-color-border);
}

.ps-admin-table-shell__filters,
.ps-admin-table-shell__actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

.ps-admin-table-shell__filters {
  min-width: 0;
  flex: 1 1 auto;
}

.ps-admin-table-shell__filters ::v-deep > .el-input {
  flex: 1 1 240px;
  width: auto;
  max-width: 360px;
}

.ps-admin-table-shell__filters ::v-deep > .el-select {
  flex: 0 1 220px;
  width: 220px;
}

.ps-admin-table-shell__actions {
  flex: 0 0 auto;
}

.ps-admin-table-shell__heading {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16px;
  padding: 15px 18px 10px;
}

h2 {
  margin: 0;
  color: var(--ps-color-text);
  font-size: 16px;
}

.ps-admin-table-shell__total {
  color: var(--ps-color-muted);
  font-size: 13px;
}

.ps-admin-table-shell__body {
  min-width: 0;
  overflow-x: auto;
  padding: 0 18px 12px;
}

.ps-admin-table-shell__footer {
  display: flex;
  justify-content: flex-end;
  padding: 14px 18px;
  border-top: 1px solid var(--ps-color-border);
}

@media (max-width: 720px) {
  .ps-admin-table-shell__toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .ps-admin-table-shell__filters,
  .ps-admin-table-shell__actions {
    width: 100%;
  }
}
</style>
