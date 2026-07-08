<template>
  <section class="status-panel" :data-testid="testId">
    <header class="status-panel__head">
      <span class="status-panel__badge" :class="statusClass">{{ statusLabel }}</span>
      <span v-if="reason" class="status-panel__reason">{{ reason }}</span>
    </header>
    <dl class="status-panel__meta">
      <div v-if="role">
        <dt>责任角色</dt>
        <dd>{{ role }}</dd>
      </div>
      <div v-if="nextStep">
        <dt>下一步</dt>
        <dd>{{ nextStep }}</dd>
      </div>
    </dl>
    <div v-if="$slots.actions" class="status-panel__actions">
      <slot name="actions" />
    </div>
  </section>
</template>

<script>
/**
 * StatusPanel：统一状态卡（NFR-UX-001）。展示当前状态、阻塞原因、责任角色、下一步和动作插槽。
 * 业务页面用 props 注入状态语义，动作按钮走具名插槽 actions，不在组件内耦合具体业务接口。
 */
export default {
  name: 'StatusPanel',
  props: {
    status: { type: String, required: true },
    statusLabel: { type: String, default: '' },
    statusClass: { type: String, default: 'info' },
    reason: { type: String, default: '' },
    role: { type: String, default: '' },
    nextStep: { type: String, default: '' },
    testId: { type: String, default: 'status-panel' }
  }
}
</script>

<style scoped>
.status-panel {
  padding: 16px 20px;
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
}

.status-panel__head {
  display: flex;
  align-items: center;
  gap: 12px;
}

.status-panel__badge {
  padding: 2px 10px;
  border-radius: 12px;
  font-size: 13px;
  font-weight: 600;
  color: #fff;
  background: #909399;
}

.status-panel__badge.info { background: #409eff; }
.status-panel__badge.success { background: #67c23a; }
.status-panel__badge.warning { background: #e6a23c; }
.status-panel__badge.error { background: #f56c6c; }

.status-panel__reason {
  color: #909399;
  font-size: 13px;
}

.status-panel__meta {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 12px;
  margin: 12px 0 0;
}

.status-panel__meta dt {
  color: #909399;
  font-size: 12px;
}

.status-panel__meta dd {
  margin: 4px 0 0;
  color: #24313d;
  font-size: 14px;
}

.status-panel__actions {
  margin-top: 12px;
  display: flex;
  gap: 12px;
}
</style>
