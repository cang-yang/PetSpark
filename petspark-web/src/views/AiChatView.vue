<template>
  <section class="ai-chat-view">
    <header class="ai-chat-view__head">
      <h2>AI 对话</h2>
      <div class="ai-chat-view__toolbar">
        <el-button v-if="consentGranted" size="small" @click="revokeConsent" data-testid="revoke-consent">撤回同意</el-button>
        <el-button size="small" type="primary" :disabled="!enabled" @click="openCreate" data-testid="new-conversation">新会话</el-button>
      </div>
    </header>

    <!-- 降级横幅：AI 未启用时显示原因。 -->
    <el-alert
      v-if="!enabled"
      :title="degradationTitle"
      type="warning"
      :closable="false"
      show-icon
      data-testid="ai-degradation-banner"
    />

    <!-- 未同意：引导同意。 -->
    <status-panel
      v-else-if="!consentGranted"
      status="empty"
      status-label="未同意 AI 服务协议"
      status-class="warning"
      reason="使用 AI 对话前需阅读并同意协议"
      role="用户"
      next-step="点击下方按钮同意协议"
      test-id="consent-required-panel"
    >
      <template #actions>
        <el-button type="primary" @click="showConsent = true" data-testid="show-consent">同意协议</el-button>
      </template>
    </status-panel>

    <!-- 主面板：会话列表 + 消息区。 -->
    <div v-else class="ai-chat-view__main">
      <aside class="ai-chat-view__sessions">
        <el-button size="small" @click="openCreate" data-testid="new-conversation-aside">+ 新会话</el-button>
        <ul v-if="conversations.length">
          <li
            v-for="conv in conversations"
            :key="conv.id"
            :class="['ai-chat-view__session', { active: conv.id === currentId }]"
            :data-testid="`session-${conv.id}`"
            @click="selectConversation(conv.id)"
          >
            <span>{{ conv.title || conv.scene }}</span>
            <el-button
              size="mini"
              type="text"
              :loading="deletingId === conv.id"
              :data-testid="`delete-session-${conv.id}`"
              @click.stop="deleteConversation(conv.id)"
            >删除</el-button>
          </li>
        </ul>
        <p v-else class="ai-chat-view__empty">暂无会话，点上方按钮开始</p>
      </aside>

      <div class="ai-chat-view__chat">
        <div v-if="!currentId" class="ai-chat-view__placeholder" data-testid="no-conversation">选择或创建一个会话开始对话</div>
        <template v-else>
          <ul class="ai-chat-view__messages" data-testid="message-list">
            <li
              v-for="msg in messages"
              :key="msg.id"
              :class="['ai-chat-view__message', `role-${msg.role}`]"
              :data-testid="`message-${msg.id}`"
            >
              <span class="ai-chat-view__role">{{ roleLabel(msg.role) }}</span>
              <span class="ai-chat-view__text">{{ msg.content }}</span>
            </li>
          </ul>

          <ai-stream-message
            v-if="streaming"
            ref="streamRenderer"
            test-id="stream-renderer"
          />

          <div class="ai-chat-view__input">
            <el-input
              v-model="text"
              type="textarea"
              :rows="2"
              placeholder="说点什么…"
              :disabled="sending"
              data-testid="message-input"
            />
            <el-button
              v-if="!streaming"
              type="primary"
              :loading="sending"
              :disabled="!text.trim() || !enabled"
              data-testid="send-message"
              @click="send"
            >发送</el-button>
            <el-button v-else type="danger" data-testid="stop-stream" @click="stopStream">停止</el-button>
          </div>
        </template>
      </div>
    </div>

    <ai-consent-dialog
      :visible.sync="showConsent"
      @consented="onConsented"
    />
  </section>
</template>

<script>
import {
  getAiStatus,
  grantAiConsent,
  withdrawAiConsent,
  createAiConversation,
  sendAiMessage,
  deleteAiConversation,
  listAiMessages,
  streamAiMessage
} from '@/api/ai'
import StatusPanel from '@/components/StatusPanel.vue'
import AiConsentDialog from '@/components/AiConsentDialog.vue'
import AiStreamMessage from '@/components/AiStreamMessage.vue'

/**
 * AI 对话视图（API-AI-001~006，路由 /ai/chat）。
 * 流程：先查 status → 未同意展示同意弹窗 → 同意后可创建会话 → 选会话后发送消息（非流式或流式）。
 * 降级：AI 未启用显示横幅并禁用发送；注入错误前端直接展示。
 */
export default {
  name: 'AiChatView',
  components: { StatusPanel, AiConsentDialog, AiStreamMessage },
  data() {
    return {
      enabled: false,
      consentGranted: false,
      degradationReason: '',
      conversations: [],
      currentId: '',
      messages: [],
      text: '',
      sending: false,
      streaming: false,
      deletingId: '',
      showConsent: false,
      abortController: null
    }
  },
  computed: {
    degradationTitle() {
      return this.degradationReason || 'AI 服务暂未启用'
    }
  },
  created() {
    this.refreshStatus()
  },
  methods: {
    async refreshStatus() {
      try {
        const res = await getAiStatus()
        const view = res.data
        this.enabled = !!view.enabled
        this.consentGranted = !!view.consentGranted
        this.degradationReason = view.degradationReason || ''
      } catch (err) {
        this.$message.error(err.message)
      }
    },
    openCreate() {
      if (!this.enabled) {
        this.$message.warning('AI 服务暂未启用，无法创建会话')
        return
      }
      if (!this.consentGranted) {
        this.showConsent = true
        return
      }
      this.createConversation()
    },
    async createConversation() {
      try {
        const res = await createAiConversation({ scene: 'PET_CHAT', title: `对话 ${this.conversations.length + 1}` })
        this.conversations.unshift(res.data)
        this.selectConversation(res.data.id)
      } catch (err) {
        this.$message.error(err.message)
      }
    },
    selectConversation(id) {
      this.currentId = id
      this.loadMessages(id)
    },
    async loadMessages(id) {
      this.messages = []
      try {
        const res = await listAiMessages(id)
        this.messages = res.data || []
      } catch (err) {
        this.$message.error(err.message)
      }
    },
    async send() {
      if (!this.text.trim()) return
      this.sending = true
      const convId = this.currentId
      const content = this.text
      this.messages.push({
        id: `local-${Date.now()}`,
        role: 'user',
        content
      })
      this.text = ''
      try {
        const res = await sendAiMessage(convId, content)
        this.messages.push({
          id: res.data.requestId,
          role: 'assistant',
          content: res.data.content + (res.data.boundaryNotice || '')
        })
      } catch (err) {
        this.$message.error(err.message)
      } finally {
        this.sending = false
      }
    },
    startStream() {
      if (!this.text.trim()) return
      const content = this.text
      this.text = ''
      this.messages.push({ id: `local-${Date.now()}`, role: 'user', content })
      this.streaming = true
      const renderer = this.$refs.streamRenderer
      if (renderer) renderer.reset()
      this.abortController = streamAiMessage(this.currentId, content, {
        onMeta: () => renderer && renderer.onMeta(),
        onDelta: (payload) => renderer && renderer.onDelta(payload),
        onUsage: (payload) => renderer && renderer.onUsage(payload),
        onDone: () => {
          if (renderer) renderer.onDone()
          this.streaming = false
          this.loadMessages(this.currentId)
        },
        onError: (message) => {
          if (renderer) renderer.onError(message)
          this.streaming = false
        }
      })
    },
    stopStream() {
      if (this.abortController) this.abortController.abort()
      this.streaming = false
    },
    async deleteConversation(id) {
      this.deletingId = id
      try {
        await deleteAiConversation(id)
        this.conversations = this.conversations.filter((c) => c.id !== id)
        if (this.currentId === id) {
          this.currentId = ''
          this.messages = []
        }
      } catch (err) {
        this.$message.error(err.message)
      } finally {
        this.deletingId = ''
      }
    },
    async onConsented() {
      this.consentGranted = true
      await this.createConversation()
    },
    async revokeConsent() {
      try {
        await withdrawAiConsent()
        this.consentGranted = false
        this.conversations = []
        this.currentId = ''
        this.messages = []
        this.$message.success('已撤回 AI 服务同意')
      } catch (err) {
        this.$message.error(err.message)
      }
    },
    roleLabel(role) {
      return role === 'user' ? '我' : 'AI'
    }
  }
}
</script>

<style scoped>
.ai-chat-view { max-width: 1000px; margin: 24px auto; padding: 24px; background: #fff; border-radius: 10px; box-shadow: 0 10px 30px rgba(36, 49, 61, 0.06); }
.ai-chat-view__head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.ai-chat-view__head h2 { margin: 0; }
.ai-chat-view__main { display: grid; grid-template-columns: 220px 1fr; gap: 16px; margin-top: 16px; }
.ai-chat-view__sessions ul { list-style: none; margin: 12px 0 0; padding: 0; }
.ai-chat-view__session { display: flex; justify-content: space-between; align-items: center; padding: 8px 10px; border-radius: 6px; cursor: pointer; }
.ai-chat-view__session.active { background: #ecf5ff; color: #409eff; }
.ai-chat-view__empty { color: #909399; font-size: 13px; }
.ai-chat-view__chat { display: flex; flex-direction: column; min-height: 360px; }
.ai-chat-view__placeholder { color: #909399; padding: 40px 0; text-align: center; }
.ai-chat-view__messages { list-style: none; margin: 0; padding: 0; flex: 1; overflow-y: auto; }
.ai-chat-view__message { padding: 8px 12px; margin-bottom: 8px; border-radius: 8px; }
.ai-chat-view__message.role-user { background: #f0f9ff; text-align: right; }
.ai-chat-view__message.role-assistant { background: #f5f7fa; }
.ai-chat-view__role { font-size: 12px; color: #909399; margin-right: 6px; }
.ai-chat-view__input { display: flex; gap: 8px; margin-top: 12px; }
</style>