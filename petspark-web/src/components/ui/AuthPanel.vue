<template>
  <section class="auth-panel">
    <div class="auth-panel__visual">
      <img :src="imageSrc" :alt="imageAlt">
      <div class="auth-panel__visual-copy">
        <strong>认真记录，安心陪伴</strong>
        <span>宠物档案、服务预约和 AI 助手都在这里。</span>
      </div>
    </div>
    <div class="auth-panel__content">
      <header>
        <h1>{{ title }}</h1>
        <p>{{ description }}</p>
      </header>
      <slot />
    </div>
  </section>
</template>

<script>
export default {
  name: 'AuthPanel',
  props: {
    title: { type: String, required: true },
    description: { type: String, required: true },
    imageSrc: { type: String, required: true },
    imageAlt: { type: String, required: true }
  }
}
</script>

<style scoped>
.auth-panel {
  display: grid;
  grid-template-columns: minmax(300px, 0.9fr) minmax(360px, 1.1fr);
  width: min(960px, 100%);
  min-height: 620px;
  overflow: hidden;
  background: #fff;
  border-radius: var(--ps-radius-lg);
  box-shadow: var(--ps-shadow-float);
}
.auth-panel__visual { position: relative; min-height: 100%; background: #eaded9; }
.auth-panel__visual::after {
  content: '';
  position: absolute;
  inset: 45% 0 0;
  background: linear-gradient(180deg, transparent, rgba(29, 38, 48, 0.72));
}
.auth-panel__visual img { width: 100%; height: 100%; object-fit: cover; }
.auth-panel__visual-copy {
  position: absolute;
  right: 28px;
  bottom: 28px;
  left: 28px;
  z-index: 1;
  display: flex;
  flex-direction: column;
  color: #fff;
}
.auth-panel__visual-copy strong { font-size: 21px; }
.auth-panel__visual-copy span { margin-top: 6px; font-size: 13px; }
.auth-panel__content {
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 44px 52px;
}
.auth-panel__content header { margin-bottom: 24px; }
.auth-panel__content h1 { margin: 0; font-size: 30px; line-height: 1.3; letter-spacing: -0.025em; }
.auth-panel__content header p { margin: 8px 0 0; color: var(--ps-color-muted); }
@media (max-width: 760px) {
  .auth-panel { grid-template-columns: 1fr; min-height: 0; }
  .auth-panel__visual { display: none; }
  .auth-panel__content { padding: 36px 32px; }
}
@media (max-width: 420px) {
  .auth-panel__content { padding: 30px 28px; }
  .auth-panel__content h1 { font-size: 26px; }
}
</style>
