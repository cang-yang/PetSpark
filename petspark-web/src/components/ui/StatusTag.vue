<template>
  <span
    class="ps-status-tag"
    :class="`ps-status-tag--${resolvedTone}`"
    :data-status="status"
  >
    <span class="ps-status-tag__dot" aria-hidden="true" />
    {{ label || status }}
  </span>
</template>

<script>
const TONES = {
  success: ['ACTIVE', 'APPROVED', 'ADOPTED', 'COMPLETED', 'PUBLISHED', 'RESOLVED', 'SENT', 'SUCCESS'],
  warning: ['CREATED', 'PENDING', 'PENDING_CONFIRMATION', 'SUBMITTED', 'ASSIGNED'],
  danger: ['REJECTED', 'FAILED', 'FAILURE', 'EXCEPTION', 'TERMINATED', 'DEAD', 'LOCKED'],
  muted: ['CANCELLED', 'WITHDRAWN', 'CLOSED', 'HIDDEN', 'INACTIVE', 'DISABLED', 'DRAFT'],
  info: ['ADOPTING', 'CONFIRMED', 'PROCESSING', 'IN_PROGRESS', 'IN_SERVICE'],
  ai: ['AI', 'AI_REVIEW', 'INTELLIGENT']
}

export default {
  name: 'StatusTag',
  props: {
    status: { type: String, required: true },
    label: { type: String, default: '' },
    tone: { type: String, default: '' }
  },
  computed: {
    resolvedTone() {
      if (this.tone) return this.tone
      const normalized = String(this.status || '').toUpperCase()
      return Object.keys(TONES).find(key => TONES[key].includes(normalized)) || 'muted'
    }
  }
}
</script>

<style scoped>
.ps-status-tag {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 24px;
  padding: 2px 9px;
  color: var(--tag-color);
  background: var(--tag-bg);
  border-radius: var(--ps-radius-pill);
  font-size: 12px;
  font-weight: 700;
  line-height: 1.4;
  white-space: nowrap;
}

.ps-status-tag__dot {
  width: 6px;
  height: 6px;
  background: currentColor;
  border-radius: 50%;
}

.ps-status-tag--success { --tag-color: #28743d; --tag-bg: #e6f4e9; }
.ps-status-tag--warning { --tag-color: #8a4d11; --tag-bg: #fff0dc; }
.ps-status-tag--danger { --tag-color: #a52f40; --tag-bg: #fde8eb; }
.ps-status-tag--muted { --tag-color: #566575; --tag-bg: #edf1f5; }
.ps-status-tag--info { --tag-color: #1f64a8; --tag-bg: #e5f0fb; }
.ps-status-tag--ai { --tag-color: #5e4baa; --tag-bg: #eeeafd; }
</style>
