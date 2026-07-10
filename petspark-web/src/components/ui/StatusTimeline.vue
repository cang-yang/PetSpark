<template>
  <ol class="status-timeline" aria-label="进度状态">
    <li
      v-for="(item, index) in items"
      :key="item.key || item.title || index"
      class="status-timeline__step"
      :data-state="stateFor(index)"
      data-testid="timeline-step"
    >
      <span class="status-timeline__marker" aria-hidden="true">{{
        index + 1
      }}</span>
      <div>
        <h3>{{ item.title }}</h3>
        <p v-if="item.description">{{ item.description }}</p>
        <time v-if="item.time">{{ item.time }}</time>
      </div>
    </li>
  </ol>
</template>

<script>
export default {
  name: 'StatusTimeline',
  props: {
    items: { type: Array, default: () => [] },
    active: { type: Number, default: 0 },
  },
  methods: {
    stateFor(index) {
      if (index < this.active) return 'done'
      if (index === this.active) return 'current'
      return 'upcoming'
    },
  },
}
</script>

<style scoped>
.status-timeline {
  display: grid;
  gap: 0;
  padding: 0;
  margin: 0;
  list-style: none;
}
.status-timeline__step {
  position: relative;
  display: grid;
  grid-template-columns: 34px 1fr;
  gap: 12px;
  min-height: 74px;
  color: var(--ps-color-muted);
}
.status-timeline__step:not(:last-child)::after {
  position: absolute;
  top: 30px;
  bottom: 0;
  left: 14px;
  width: 2px;
  content: '';
  background: var(--ps-color-border);
}
.status-timeline__marker {
  position: relative;
  z-index: 1;
  display: grid;
  width: 30px;
  height: 30px;
  place-items: center;
  background: var(--ps-color-surface-soft);
  border: 2px solid var(--ps-color-border);
  border-radius: 50%;
  font-size: 12px;
  font-weight: 800;
}
.status-timeline__step[data-state='done'] .status-timeline__marker {
  color: #fff;
  background: var(--ps-color-green);
  border-color: var(--ps-color-green);
}
.status-timeline__step[data-state='current'] .status-timeline__marker {
  color: #fff;
  background: var(--ps-color-pink);
  border-color: var(--ps-color-pink);
  box-shadow: var(--ps-focus-ring);
}
.status-timeline h3 {
  margin: 2px 0 0;
  color: var(--ps-color-text);
  font-size: 15px;
}
.status-timeline p {
  margin: 4px 0 0;
  font-size: 13px;
}
.status-timeline time {
  display: block;
  margin-top: 3px;
  font-size: 12px;
}
</style>
