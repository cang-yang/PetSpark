<template>
  <router-link class="feature-card ps-motion-lift" :to="to" :data-tone="tone">
    <span class="feature-card__icon" aria-hidden="true"><AppIcon :name="icon" /></span>
    <span class="feature-card__body">
      <strong>{{ title }}</strong>
      <small>{{ description }}</small>
    </span>
    <span class="feature-card__arrow" aria-hidden="true">→</span>
  </router-link>
</template>

<script>
import AppIcon from '@/components/ui/AppIcon.vue'

export default {
  name: 'FeatureCard',
  components: { AppIcon },
  props: {
    title: { type: String, required: true },
    description: { type: String, required: true },
    icon: { type: String, required: true },
    to: { type: [String, Object], required: true },
    tone: { type: String, default: 'pink' }
  }
}
</script>

<style scoped>
.feature-card {
  display: flex;
  align-items: center;
  gap: 14px;
  min-height: 104px;
  padding: 18px;
  color: var(--ps-color-text);
  background: #fff;
  border-radius: var(--ps-radius-md);
  text-decoration: none;
  box-shadow: var(--ps-shadow-card);
}
.feature-card__icon {
  display: grid;
  flex: 0 0 auto;
  width: 46px;
  height: 46px;
  place-items: center;
  color: var(--ps-color-pink-dark);
  background: #fde8ef;
  border-radius: 14px;
  font-size: 22px;
}
.feature-card[data-tone="green"] .feature-card__icon { color: #326f42; background: #e5f3e8; }
.feature-card[data-tone="purple"] .feature-card__icon { color: #5542a8; background: #ebe8fb; }
.feature-card[data-tone="peach"] .feature-card__icon { color: #9a4d2c; background: #feebe2; }
.feature-card__body { display: flex; min-width: 0; flex-direction: column; }
.feature-card__body strong { font-size: 16px; }
.feature-card__body small { margin-top: 4px; color: var(--ps-color-muted); font-size: 13px; }
.feature-card__arrow { margin-left: auto; color: var(--ps-color-muted); font-size: 18px; }
</style>
