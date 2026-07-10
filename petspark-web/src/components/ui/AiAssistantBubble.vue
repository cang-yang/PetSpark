<template>
  <router-link class="ai-assistant-bubble" to="/ai/chat" aria-label="打开 PetSpark AI 宠物助手">
    <span class="ai-assistant-bubble__avatar" aria-hidden="true">
      <img :src="avatar" alt="">
      <span class="ai-assistant-bubble__status" />
    </span>
    <span class="ai-assistant-bubble__copy">
      <strong>问问派派</strong>
      <small>AI 宠物伙伴</small>
    </span>
  </router-link>
</template>

<script>
import avatar from '@/assets/brand/ai-assistant-avatar.webp'

export default {
  name: 'AiAssistantBubble',
  data() {
    return { avatar }
  }
}
</script>

<style scoped>
.ai-assistant-bubble {
  position: fixed;
  right: max(20px, calc((100vw - var(--ps-page-max)) / 2 - 68px));
  bottom: 24px;
  z-index: calc(var(--ps-z-sticky) - 1);
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 13px 8px 8px;
  color: #332b56;
  background: rgba(255, 255, 255, 0.94);
  border: 1px solid rgba(114, 95, 197, 0.22);
  border-radius: 18px;
  box-shadow: 0 10px 30px rgba(62, 48, 116, 0.16);
  text-decoration: none;
  backdrop-filter: blur(12px);
  transition: transform var(--ps-motion-normal) var(--ps-ease-out), box-shadow var(--ps-motion-normal) ease;
}
.ai-assistant-bubble:hover { transform: translateY(-3px); box-shadow: 0 14px 34px rgba(62, 48, 116, 0.22); }
.ai-assistant-bubble__avatar { position: relative; width: 48px; height: 48px; flex: 0 0 48px; }
.ai-assistant-bubble__avatar img { width: 100%; height: 100%; object-fit: cover; border-radius: 15px; }
.ai-assistant-bubble__status {
  position: absolute;
  right: -2px;
  bottom: -1px;
  width: 11px;
  height: 11px;
  background: #54a968;
  border: 2px solid #fff;
  border-radius: 50%;
  animation: ps-assistant-breathe 2.6s ease-in-out infinite;
}
.ai-assistant-bubble__copy { display: flex; flex-direction: column; line-height: 1.2; }
.ai-assistant-bubble__copy strong { font-size: 14px; }
.ai-assistant-bubble__copy small { margin-top: 3px; color: var(--ps-color-muted); font-size: 11px; }
@keyframes ps-assistant-breathe { 0%, 100% { transform: scale(0.9); } 50% { transform: scale(1.1); } }
@media (max-width: 600px) {
  .ai-assistant-bubble { right: 14px; bottom: 14px; padding: 7px; border-radius: 16px; }
  .ai-assistant-bubble__copy { display: none; }
}
</style>
