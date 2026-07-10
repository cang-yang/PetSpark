<template>
  <section class="page-hero">
    <div class="page-hero__copy">
      <p v-if="label" class="page-hero__label">{{ label }}</p>
      <h1>{{ title }}</h1>
      <p class="page-hero__description">{{ description }}</p>
      <div class="page-hero__actions">
        <router-link class="page-hero__primary" :to="primaryTo">{{ primaryText }}</router-link>
        <router-link
          v-if="secondaryText"
          class="page-hero__secondary"
          :to="secondaryTo"
        >{{ secondaryText }}</router-link>
      </div>
    </div>
    <div class="page-hero__visual">
      <img v-if="imageSrc" :src="imageSrc" :alt="imageAlt">
      <slot name="visual" />
    </div>
  </section>
</template>

<script>
export default {
  name: 'PageHero',
  props: {
    label: { type: String, default: '' },
    title: { type: String, required: true },
    description: { type: String, required: true },
    primaryText: { type: String, required: true },
    primaryTo: { type: [String, Object], required: true },
    secondaryText: { type: String, default: '' },
    secondaryTo: { type: [String, Object], default: '/' },
    imageSrc: { type: String, default: '' },
    imageAlt: { type: String, default: '' }
  }
}
</script>

<style scoped>
.page-hero {
  position: relative;
  display: grid;
  grid-template-columns: minmax(0, 0.92fr) minmax(360px, 1.08fr);
  min-height: 470px;
  overflow: hidden;
  background: #fff;
  border-radius: var(--ps-radius-lg);
}
.page-hero__copy {
  z-index: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 54px 28px 54px 52px;
}
.page-hero__label {
  width: fit-content;
  margin: 0 0 16px;
  padding: 5px 10px;
  color: var(--ps-color-pink-dark);
  background: #fde8ef;
  border-radius: var(--ps-radius-pill);
  font-size: 13px;
  font-weight: 700;
}
.page-hero h1 {
  max-width: 12ch;
  margin: 0;
  font-size: clamp(38px, 4.4vw, 62px);
  line-height: 1.12;
  letter-spacing: -0.035em;
}
.page-hero__description {
  max-width: 34em;
  margin: 20px 0 0;
  color: var(--ps-color-muted);
  font-size: 17px;
  line-height: 1.8;
}
.page-hero__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 30px;
}
.page-hero__actions a {
  padding: 11px 18px;
  border-radius: var(--ps-radius-sm);
  text-decoration: none;
  font-weight: 700;
  transition: transform var(--ps-motion-fast) var(--ps-ease-out), background var(--ps-motion-fast) ease;
}
.page-hero__actions a:hover { transform: translateY(-2px); }
.page-hero__primary { color: #fff; background: var(--ps-color-pink); }
.page-hero__primary:hover { background: var(--ps-color-pink-dark); }
.page-hero__secondary { color: var(--ps-color-text); background: var(--ps-color-surface-soft); }
.page-hero__secondary:hover { background: #e7ebf0; }
.page-hero__visual {
  position: relative;
  min-height: 360px;
  background: #f6e8e9;
}
.page-hero__visual img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
@media (max-width: 900px) {
  .page-hero { grid-template-columns: 1fr; }
  .page-hero__copy { padding: 38px 32px; }
  .page-hero h1 { max-width: 15ch; }
  .page-hero__visual { min-height: 320px; }
}
@media (max-width: 600px) {
  .page-hero { min-height: 0; }
  .page-hero__copy { padding: 30px 24px; }
  .page-hero h1 { font-size: 38px; }
  .page-hero__description { font-size: 15px; }
  .page-hero__actions { flex-direction: column; }
  .page-hero__actions a { text-align: center; }
  .page-hero__visual { min-height: 230px; }
}
</style>
