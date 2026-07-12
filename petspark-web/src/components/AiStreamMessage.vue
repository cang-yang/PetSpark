<template>
  <div class="ai-stream-message" :data-testid="testId">
    <div v-if="status === 'idle'" class="ai-stream-message__placeholder">{{ placeholder }}</div>
    <div v-else-if="status === 'streaming'" class="ai-stream-message__streaming">
      <span class="ai-stream-message__avatar">派</span>
      <SafeMarkdown v-if="content" class="ai-stream-message__content" :content="content" data-testid="stream-content" />
      <span v-else class="ai-stream-message__thinking">派派正在整理建议<span class="ai-stream-message__dots"><i /><i /><i /></span></span>
    </div>
    <div v-else-if="status === 'error'" class="ai-stream-message__error" data-testid="stream-error">
      <span class="ai-stream-message__avatar error">!</span>
      <span><strong>这次没有收到回复</strong><small>{{ errorMessage }}</small></span>
    </div>
    <div v-else class="ai-stream-message__done">
      <SafeMarkdown class="ai-stream-message__content" :content="content" data-testid="stream-content" />
      <p v-if="boundaryNotice" class="ai-stream-message__notice">{{ boundaryNotice }}</p>
      <div v-if="totalTokens != null" class="ai-stream-message__usage">
        tokens: {{ totalTokens }}
      </div>
    </div>
  </div>
</template>

<script>
import SafeMarkdown from '@/components/ui/SafeMarkdown.vue'
/**
 * AiStreamMessage：渲染 SSE 流式响应阶段。
 * 由父组件通过 events 注入 meta/delta/usage/done/error 后切换 status。
 */
export default {
  name: 'AiStreamMessage',
  components: { SafeMarkdown },
  props: {
    testId: { type: String, default: 'ai-stream-message' },
    placeholder: { type: String, default: '等待 AI 回复…' }
  },
  data() {
    return {
      status: 'idle',
      content: '',
      boundaryNotice: '',
      totalTokens: null,
      errorMessage: ''
    }
  },
  methods: {
    reset() {
      this.status = 'streaming'
      this.content = ''
      this.boundaryNotice = ''
      this.totalTokens = null
      this.errorMessage = ''
    },
    onMeta(payload) {
      this.status = 'streaming'
    },
    onDelta(payload) {
      if (payload && payload.content) this.content += payload.content
    },
    onUsage(payload) {
      if (payload && payload.totalTokens != null) this.totalTokens = payload.totalTokens
    },
    onDone() {
      if (this.status !== 'error') this.status = 'done'
    },
    onError(message) {
      this.status = 'error'
      this.errorMessage = message || 'AI 回复失败'
    }
  }
}
</script>

<style scoped>
.ai-stream-message { margin-top: 14px; }
.ai-stream-message__streaming,
.ai-stream-message__error { display: flex; align-items: center; gap: 10px; width: fit-content; padding: 10px 14px 10px 10px; background: #fff; border: 1px solid #ebe7f8; border-radius: 6px 18px 18px; box-shadow: 0 8px 24px rgba(74, 61, 129, .08); color: #746d86; }
.ai-stream-message__streaming { max-width: min(760px, 100%); align-items: flex-start; }
.ai-stream-message__content { min-width: 0; color: #34313d; }
.ai-stream-message__avatar { display: grid; width: 34px; height: 34px; place-items: center; flex: 0 0 34px; color: #fff; background: linear-gradient(145deg, #7564cf, #d94b7d); border-radius: 11px; font-size: 12px; font-weight: 800; }
.ai-stream-message__avatar.error { background: linear-gradient(145deg, #f08c78, #d84a66); }
.ai-stream-message__thinking { display: flex; align-items: flex-end; gap: 7px; }
.ai-stream-message__dots { display: inline-flex; gap: 3px; padding-bottom: 3px; }
.ai-stream-message__dots i { width: 4px; height: 4px; background: #8a79d6; border-radius: 50%; animation: bounce 1.1s infinite ease-in-out; }
.ai-stream-message__dots i:nth-child(2) { animation-delay: .14s; }
.ai-stream-message__dots i:nth-child(3) { animation-delay: .28s; }
@keyframes bounce { 0%, 60%, 100% { transform: translateY(0); opacity: .35; } 30% { transform: translateY(-4px); opacity: 1; } }
.ai-stream-message__error { color: #bc3f56; background: #fff9f8; border-color: #f4d8d7; }
.ai-stream-message__error > span:last-child { display: grid; gap: 2px; }
.ai-stream-message__error small { color: #8f6e73; }
.ai-stream-message__notice { color: #909399; font-size: 12px; margin-top: 6px; }
.ai-stream-message__usage { color: #c0c4cc; font-size: 12px; margin-top: 6px; }
</style>
