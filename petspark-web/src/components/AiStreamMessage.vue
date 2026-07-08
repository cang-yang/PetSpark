<template>
  <div class="ai-stream-message" :data-testid="testId">
    <div v-if="status === 'idle'" class="ai-stream-message__placeholder">{{ placeholder }}</div>
    <div v-else-if="status === 'streaming'" class="ai-stream-message__streaming">
      <span class="ai-stream-message__dot" />
      <span>AI 正在回复…</span>
    </div>
    <div v-else-if="status === 'error'" class="ai-stream-message__error" data-testid="stream-error">
      <el-tag size="mini" type="danger">错误</el-tag>
      <span>{{ errorMessage }}</span>
    </div>
    <div v-else class="ai-stream-message__done">
      <p class="ai-stream-message__content" data-testid="stream-content">{{ content }}</p>
      <p v-if="boundaryNotice" class="ai-stream-message__notice">{{ boundaryNotice }}</p>
      <div v-if="totalTokens != null" class="ai-stream-message__usage">
        tokens: {{ totalTokens }}
      </div>
    </div>
  </div>
</template>

<script>
/**
 * AiStreamMessage：渲染 SSE 流式响应阶段。
 * 由父组件通过 events 注入 meta/delta/usage/done/error 后切换 status。
 */
export default {
  name: 'AiStreamMessage',
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
.ai-stream-message__streaming { display: flex; align-items: center; gap: 8px; color: #909399; }
.ai-stream-message__dot { width: 8px; height: 8px; border-radius: 50%; background: #409eff; animation: pulse 1s infinite; }
@keyframes pulse { 0% { opacity: 1; } 50% { opacity: 0.3; } 100% { opacity: 1; } }
.ai-stream-message__error { display: flex; align-items: center; gap: 8px; color: #f56c6c; }
.ai-stream-message__notice { color: #909399; font-size: 12px; margin-top: 6px; }
.ai-stream-message__usage { color: #c0c4cc; font-size: 12px; margin-top: 6px; }
</style>