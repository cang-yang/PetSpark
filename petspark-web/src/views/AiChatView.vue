<template>
  <section class="ai-chat-view">
    <header class="ai-chat-view__hero">
      <div class="ai-chat-view__identity">
        <img :src="assistantAvatar" alt="派派 AI 宠物助手头像">
        <div>
          <p>PetSpark AI 宠物伙伴</p>
          <h1>和派派聊聊它的日常</h1>
          <span>提供护理信息与陪伴建议，不替代兽医诊断；紧急情况请及时联系专业机构。</span>
        </div>
      </div>
      <div class="ai-chat-view__toolbar">
        <el-button v-if="consentGranted" size="small" @click="revokeConsent" data-testid="revoke-consent">撤回同意</el-button>
        <el-button size="small" type="primary" :disabled="!enabled" @click="openCreate" data-testid="new-conversation">开启新话题</el-button>
      </div>
    </header>

    <el-alert
      v-if="!enabled"
      :title="degradationTitle"
      type="warning"
      :closable="false"
      show-icon
      data-testid="ai-degradation-banner"
    />

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
        <el-button type="primary" @click="showConsent = true" data-testid="show-consent">阅读并同意</el-button>
      </template>
    </status-panel>

    <div v-else class="ai-chat-view__main">
      <aside class="ai-chat-view__sessions">
        <div class="ai-chat-view__sessions-head">
          <strong>我的话题</strong>
          <span>{{ conversations.length }} 个</span>
        </div>
        <button class="ai-chat-view__new" type="button" @click="openCreate" data-testid="new-conversation-aside">
          <span aria-hidden="true">＋</span> 新建对话
        </button>
        <ul v-if="conversations.length">
          <li
            v-for="conv in conversations"
            :key="conv.id"
            :class="['ai-chat-view__session', { active: conv.id === currentId }]"
            :data-testid="`session-${conv.id}`"
            @click="selectConversation(conv.id)"
          >
            <span class="ai-chat-view__session-icon" aria-hidden="true">聊</span>
            <span class="ai-chat-view__session-name">{{ conv.title || conv.scene }}</span>
            <el-button
              size="mini"
              type="text"
              :loading="deletingId === conv.id"
              :data-testid="`delete-session-${conv.id}`"
              @click.stop="deleteConversation(conv.id)"
            >删除</el-button>
          </li>
        </ul>
        <p v-else class="ai-chat-view__empty">还没有保存的话题</p>
        <div class="ai-chat-view__privacy">
          <strong>隐私提醒</strong>
          <span>请勿发送手机号、地址或证件号码等敏感信息。</span>
        </div>
      </aside>

      <div class="ai-chat-view__chat">
        <div v-if="!currentId" class="ai-chat-view__placeholder" data-testid="no-conversation">
          <div class="ai-chat-view__empty-art" data-testid="ai-empty-state">
            <img :src="emptyChatArtwork" alt="派派和猫咪等待开启对话">
          </div>
          <h2>今天想聊些什么？</h2>
          <p>选择一个常见问题，派派会先帮你整理日常护理思路。</p>
          <div class="ai-chat-view__examples">
            <button
              v-for="prompt in examplePrompts"
              :key="prompt"
              type="button"
              data-testid="example-prompt"
              @click="useExample(prompt)"
            >{{ prompt }}</button>
          </div>
        </div>
        <template v-else>
          <div class="ai-chat-view__chat-head">
            <div><span class="ai-chat-view__online" />派派在线</div>
            <small>回答仅供日常信息参考</small>
          </div>
          <div class="ai-chat-view__conversation">
            <div v-if="!messages.length" class="ai-chat-view__starter">
              <strong>新话题已准备好</strong>
              <span>可以直接输入，也可以选择一个示例：</span>
              <button v-for="prompt in examplePrompts" :key="prompt" type="button" @click="text = prompt">{{ prompt }}</button>
            </div>
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
            <ai-stream-message v-if="streaming" ref="streamRenderer" test-id="stream-renderer" />
          </div>

          <div class="ai-chat-view__composer">
            <el-input
              v-model="text"
              type="textarea"
              :rows="2"
              maxlength="1000"
              show-word-limit
              placeholder="描述宠物的情况，尽量包含年龄、持续时间和近期变化…"
              :disabled="sending || streaming"
              data-testid="message-input"
              @keyup.ctrl.enter.native="submitMessage"
            />
            <div class="ai-chat-view__composer-actions">
              <label><input v-model="streamMode" type="checkbox"> 流式回复</label>
              <span>Ctrl + Enter 发送</span>
              <el-button
                v-if="!streaming"
                type="primary"
                :loading="sending"
                :disabled="!text.trim() || !enabled"
                data-testid="send-message"
                @click="submitMessage"
              >发送</el-button>
              <el-button v-else type="danger" data-testid="stop-stream" @click="stopStream">停止</el-button>
            </div>
          </div>
        </template>
      </div>
    </div>

    <ai-consent-dialog :visible.sync="showConsent" @consented="onConsented" />
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
import assistantAvatar from '@/assets/brand/ai-assistant-avatar.webp'
import emptyChatArtwork from '@/assets/illustrations/ai-empty-chat.webp'

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
      abortController: null,
      streamMode: false,
      assistantAvatar,
      emptyChatArtwork,
      examplePrompts: [
        '幼猫刚到家，需要先准备什么？',
        '狗狗最近食欲下降，该观察哪些情况？',
        '怎么安排宠物的日常清洁和驱虫？'
      ]
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
    submitMessage() {
      if (this.streamMode) this.startStream()
      else this.send()
    },
    async useExample(prompt) {
      this.text = prompt
      if (!this.currentId) await this.createConversation()
    },
    startStream() {
      if (!this.text.trim()) return
      const content = this.text
      this.text = ''
      this.messages.push({ id: `local-${Date.now()}`, role: 'user', content })
      this.streaming = true
      this.$nextTick(() => {
        const renderer = this.$refs.streamRenderer
        if (renderer && typeof renderer.reset === 'function') renderer.reset()
        this.abortController = streamAiMessage(this.currentId, content, {
          onMeta: () => renderer && typeof renderer.onMeta === 'function' && renderer.onMeta(),
          onDelta: (payload) => renderer && typeof renderer.onDelta === 'function' && renderer.onDelta(payload),
          onUsage: (payload) => renderer && typeof renderer.onUsage === 'function' && renderer.onUsage(payload),
          onDone: () => {
            if (renderer && typeof renderer.onDone === 'function') renderer.onDone()
            this.streaming = false
            this.loadMessages(this.currentId)
          },
          onError: (message) => {
            if (renderer && typeof renderer.onError === 'function') renderer.onError(message)
            this.streaming = false
          }
        })
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
.ai-chat-view { display: grid; gap: 18px; max-width: 1120px; margin: 0 auto; }
.ai-chat-view__hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 28px;
  padding: 26px 30px;
  background: rgba(255, 255, 255, 0.90);
  border: 1px solid rgba(114, 95, 197, 0.16);
  border-radius: var(--ps-radius-lg);
  box-shadow: 0 12px 34px rgba(67, 52, 126, 0.09);
  backdrop-filter: blur(14px);
}
.ai-chat-view__identity { display: flex; align-items: center; gap: 18px; min-width: 0; }
.ai-chat-view__identity img { width: 74px; height: 74px; flex: 0 0 74px; object-fit: cover; border-radius: 22px; box-shadow: 0 8px 18px rgba(114, 95, 197, 0.16); }
.ai-chat-view__identity p { margin: 0 0 2px; color: var(--ps-color-purple); font-size: 13px; font-weight: 750; }
.ai-chat-view__identity h1 { margin: 0; font-size: clamp(25px, 3vw, 34px); line-height: 1.24; letter-spacing: -0.025em; }
.ai-chat-view__identity span { display: block; max-width: 68ch; margin-top: 5px; color: var(--ps-color-muted); font-size: 13px; }
.ai-chat-view__toolbar { display: flex; flex: 0 0 auto; gap: 8px; }
.ai-chat-view__main {
  display: grid;
  grid-template-columns: 248px minmax(0, 1fr);
  min-height: 610px;
  overflow: hidden;
  background: rgba(255, 255, 255, 0.94);
  border: 1px solid rgba(114, 95, 197, 0.16);
  border-radius: var(--ps-radius-lg);
  box-shadow: 0 14px 38px rgba(67, 52, 126, 0.10);
  backdrop-filter: blur(16px);
}
.ai-chat-view__sessions { display: flex; flex-direction: column; min-width: 0; padding: 20px 16px; background: rgba(246, 243, 255, 0.72); border-right: 1px solid rgba(114, 95, 197, 0.14); }
.ai-chat-view__sessions-head { display: flex; align-items: center; justify-content: space-between; padding: 0 4px 14px; }
.ai-chat-view__sessions-head span { color: var(--ps-color-muted); font-size: 12px; }
.ai-chat-view__new { width: 100%; padding: 10px 12px; color: #fff; background: #5d4eaa; border: 0; border-radius: var(--ps-radius-sm); font-weight: 700; cursor: pointer; }
.ai-chat-view__new:hover { background: #493d8d; }
.ai-chat-view__sessions ul { display: grid; gap: 6px; max-height: 360px; margin: 14px 0 0; padding: 0; overflow-y: auto; list-style: none; }
.ai-chat-view__session { display: grid; grid-template-columns: 30px minmax(0, 1fr) auto; align-items: center; gap: 7px; padding: 8px; border-radius: var(--ps-radius-sm); cursor: pointer; }
.ai-chat-view__session:hover { background: rgba(255, 255, 255, 0.70); }
.ai-chat-view__session.active { color: #493d8d; background: #fff; box-shadow: 0 3px 10px rgba(67, 52, 126, 0.08); }
.ai-chat-view__session-icon { display: grid; width: 28px; height: 28px; place-items: center; color: #5d4eaa; background: #ece8ff; border-radius: 9px; font-size: 11px; font-weight: 800; }
.ai-chat-view__session-name { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.ai-chat-view__empty { margin: 14px 4px; color: var(--ps-color-muted); font-size: 13px; }
.ai-chat-view__privacy { display: grid; gap: 4px; margin-top: auto; padding: 13px; color: #554b78; background: rgba(255, 255, 255, 0.68); border-radius: var(--ps-radius-sm); }
.ai-chat-view__privacy strong { font-size: 12px; }
.ai-chat-view__privacy span { color: var(--ps-color-muted); font-size: 11px; line-height: 1.55; }
.ai-chat-view__chat { display: flex; min-width: 0; min-height: 610px; flex-direction: column; }
.ai-chat-view__placeholder { display: flex; flex: 1; align-items: center; flex-direction: column; justify-content: center; padding: 34px; text-align: center; }
.ai-chat-view__empty-art { width: min(430px, 80%); overflow: hidden; border-radius: 26px; }
.ai-chat-view__empty-art img { width: 100%; aspect-ratio: 4 / 2.8; object-fit: cover; }
.ai-chat-view__placeholder h2 { margin: 18px 0 0; font-size: 24px; }
.ai-chat-view__placeholder > p { margin: 7px 0 0; color: var(--ps-color-muted); }
.ai-chat-view__examples { display: flex; flex-wrap: wrap; justify-content: center; gap: 8px; margin-top: 20px; }
.ai-chat-view__examples button,
.ai-chat-view__starter button { padding: 8px 11px; color: #4c407e; background: #f3f0ff; border: 1px solid #ded8fa; border-radius: var(--ps-radius-sm); cursor: pointer; }
.ai-chat-view__examples button:hover,
.ai-chat-view__starter button:hover { color: #fff; background: #5d4eaa; border-color: #5d4eaa; }
.ai-chat-view__chat-head { display: flex; align-items: center; justify-content: space-between; min-height: 50px; padding: 10px 20px; border-bottom: 1px solid var(--ps-color-border); }
.ai-chat-view__chat-head > div { display: flex; align-items: center; gap: 8px; font-weight: 700; }
.ai-chat-view__chat-head small { color: var(--ps-color-muted); }
.ai-chat-view__online { width: 8px; height: 8px; background: #54a968; border-radius: 50%; box-shadow: 0 0 0 4px rgba(84, 169, 104, 0.12); }
.ai-chat-view__conversation { display: flex; flex: 1; min-height: 0; flex-direction: column; padding: 22px; overflow-y: auto; }
.ai-chat-view__starter { display: flex; align-items: flex-start; flex-direction: column; gap: 8px; width: min(520px, 100%); margin: auto; padding: 24px; background: #faf9ff; border: 1px solid #e8e4f8; border-radius: var(--ps-radius-md); }
.ai-chat-view__starter > span { margin-bottom: 4px; color: var(--ps-color-muted); }
.ai-chat-view__messages { display: flex; flex-direction: column; gap: 12px; margin: 0; padding: 0; list-style: none; }
.ai-chat-view__message { display: grid; grid-template-columns: 34px minmax(0, auto); align-items: start; gap: 8px; max-width: 82%; }
.ai-chat-view__message.role-user { align-self: flex-end; grid-template-columns: minmax(0, auto) 34px; }
.ai-chat-view__message.role-user .ai-chat-view__role { grid-column: 2; grid-row: 1; color: #fff; background: var(--ps-color-pink); }
.ai-chat-view__message.role-user .ai-chat-view__text { grid-column: 1; grid-row: 1; color: #fff; background: #5d4eaa; border-radius: 14px 4px 14px 14px; }
.ai-chat-view__role { display: grid; width: 34px; height: 34px; place-items: center; color: #5d4eaa; background: #ece8ff; border-radius: 11px; font-size: 11px; font-weight: 800; }
.ai-chat-view__text { padding: 10px 13px; background: #f4f5f7; border-radius: 4px 14px 14px; line-height: 1.7; white-space: pre-wrap; }
.ai-chat-view__composer { padding: 14px 18px 16px; background: #fff; border-top: 1px solid var(--ps-color-border); }
.ai-chat-view__composer-actions { display: flex; align-items: center; gap: 14px; margin-top: 9px; color: var(--ps-color-muted); font-size: 12px; }
.ai-chat-view__composer-actions label { display: flex; align-items: center; gap: 5px; cursor: pointer; }
.ai-chat-view__composer-actions span { margin-left: auto; }
@media (max-width: 900px) {
  .ai-chat-view__hero { align-items: flex-start; flex-direction: column; }
  .ai-chat-view__main { grid-template-columns: 1fr; }
  .ai-chat-view__sessions { border-right: 0; border-bottom: 1px solid rgba(114, 95, 197, 0.14); }
  .ai-chat-view__sessions ul { display: flex; max-height: none; overflow-x: auto; }
  .ai-chat-view__session { min-width: 190px; }
  .ai-chat-view__privacy { display: none; }
  .ai-chat-view__chat { min-height: 560px; }
}
@media (max-width: 600px) {
  .ai-chat-view__hero { padding: 20px; }
  .ai-chat-view__identity { align-items: flex-start; }
  .ai-chat-view__identity img { width: 54px; height: 54px; flex-basis: 54px; border-radius: 17px; }
  .ai-chat-view__identity h1 { font-size: 24px; }
  .ai-chat-view__toolbar { width: 100%; flex-wrap: wrap; }
  .ai-chat-view__main { min-height: 0; }
  .ai-chat-view__sessions { padding: 16px; }
  .ai-chat-view__chat { min-height: 520px; }
  .ai-chat-view__placeholder { padding: 28px 18px; }
  .ai-chat-view__empty-art { width: 100%; }
  .ai-chat-view__examples { display: grid; width: 100%; }
  .ai-chat-view__message { max-width: 94%; }
  .ai-chat-view__composer-actions span { display: none; }
  .ai-chat-view__composer-actions .el-button { margin-left: auto; }
}
</style>
