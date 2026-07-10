<template>
  <article class="order-status-card">
    <header class="order-status-card__header">
      <div>
        <p>{{ label }}</p>
        <h2>{{ reference }}</h2>
      </div>
      <span
        :class="[
          'order-status-card__status',
          `order-status-card__status--${tone}`,
        ]"
        >{{ resolvedStatusLabel }}</span
      >
    </header>
    <div class="order-status-card__content">
      <dl>
        <div v-if="subject">
          <dt>服务对象</dt>
          <dd>{{ subject }}</dd>
        </div>
        <div v-if="dateText">
          <dt>时间</dt>
          <dd>{{ dateText }}</dd>
        </div>
        <div v-if="amountText">
          <dt>金额</dt>
          <dd>{{ amountText }}</dd>
        </div>
      </dl>
      <slot />
      <p v-if="nextStep" class="order-status-card__next">
        <span>下一步</span>{{ nextStep }}
      </p>
    </div>
    <footer
      v-if="actionText || secondaryActionText || $slots.actions"
      class="order-status-card__actions"
    >
      <slot name="actions">
        <el-button
          v-if="secondaryActionText"
          size="small"
          @click="$emit('secondary-action', order)"
          >{{ secondaryActionText }}</el-button
        >
        <el-button
          v-if="actionText"
          type="primary"
          size="small"
          @click="$emit('action', order)"
          >{{ actionText }}</el-button
        >
      </slot>
    </footer>
  </article>
</template>

<script>
const STATUS_LABELS = {
  CREATED: '已创建',
  PROCESSING: '处理中',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
  PENDING: '待审核',
  APPROVED: '已通过',
  REJECTED: '已驳回',
  WITHDRAWN: '已撤回',
  PENDING_CONFIRMATION: '待确认',
  CONFIRMED: '已确认',
  IN_SERVICE: '照护中',
  TERMINATED: '已终止',
}

export default {
  name: 'OrderStatusCard',
  props: {
    order: { type: Object, required: true },
    label: { type: String, default: '业务单号' },
    statusLabel: { type: String, default: '' },
    nextStep: { type: String, default: '' },
    actionText: { type: String, default: '' },
    secondaryActionText: { type: String, default: '' },
  },
  computed: {
    reference() {
      return (
        this.order.orderNo ||
        this.order.bookingNo ||
        this.order.applicationNo ||
        `#${this.order.id || '-'}`
      )
    },
    resolvedStatusLabel() {
      return (
        this.statusLabel ||
        STATUS_LABELS[this.order.status] ||
        this.order.status ||
        '状态待确认'
      )
    },
    tone() {
      if (['COMPLETED', 'APPROVED', 'CONFIRMED'].includes(this.order.status))
        return 'success'
      if (
        ['CANCELLED', 'REJECTED', 'WITHDRAWN', 'TERMINATED'].includes(
          this.order.status
        )
      )
        return 'muted'
      return 'progress'
    },
    subject() {
      return this.order.petName || this.order.subjectName || ''
    },
    dateText() {
      if (this.order.startDate || this.order.endDate)
        return [this.order.startDate, this.order.endDate]
          .filter(Boolean)
          .join(' — ')
      return this.formatTime(this.order.createdAt)
    },
    amountText() {
      const value = this.order.totalAmount ?? this.order.amount
      return value === undefined || value === null || value === ''
        ? ''
        : `￥${value}`
    },
  },
  methods: {
    formatTime(value) {
      if (!value) return ''
      const date = new Date(value)
      if (Number.isNaN(date.getTime())) return String(value)
      return new Intl.DateTimeFormat('zh-CN', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
      }).format(date)
    },
  },
}
</script>

<style scoped>
.order-status-card {
  overflow: hidden;
  background: var(--ps-color-surface);
  border: 1px solid var(--ps-color-border);
  border-radius: var(--ps-radius-lg);
  box-shadow: var(--ps-shadow-card);
}
.order-status-card__header {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  justify-content: space-between;
  padding: 18px 20px;
  background: linear-gradient(135deg, var(--ps-color-cream), #fff);
  border-bottom: 1px solid var(--ps-color-border);
}
.order-status-card__header p {
  margin: 0;
  color: var(--ps-color-muted);
  font-size: 12px;
}
.order-status-card__header h2 {
  margin: 3px 0 0;
  font-size: 18px;
}
.order-status-card__status {
  padding: 5px 10px;
  border-radius: var(--ps-radius-pill);
  font-size: 12px;
  font-weight: 700;
}
.order-status-card__status--success {
  color: var(--ps-color-green);
  background: rgba(79, 156, 99, 0.12);
}
.order-status-card__status--progress {
  color: var(--ps-color-warning);
  background: rgba(247, 129, 85, 0.13);
}
.order-status-card__status--muted {
  color: var(--ps-color-muted);
  background: var(--ps-color-surface-soft);
}
.order-status-card__content {
  padding: 18px 20px;
}
.order-status-card dl {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
  margin: 0;
}
.order-status-card dl div {
  min-width: 0;
}
.order-status-card dt {
  color: var(--ps-color-muted);
  font-size: 12px;
}
.order-status-card dd {
  margin: 2px 0 0;
  font-weight: 700;
  overflow-wrap: anywhere;
}
.order-status-card__next {
  display: flex;
  gap: 10px;
  margin: 16px 0 0;
  padding: 11px 13px;
  color: var(--ps-color-text);
  background: var(--ps-color-surface-soft);
  border-radius: var(--ps-radius-sm);
  font-size: 13px;
}
.order-status-card__next span {
  color: var(--ps-color-pink);
  font-weight: 700;
}
.order-status-card__actions {
  display: flex;
  gap: 10px;
  justify-content: flex-end;
  padding: 0 20px 18px;
}

@media (max-width: 520px) {
  .order-status-card dl {
    grid-template-columns: 1fr;
  }
}
</style>
