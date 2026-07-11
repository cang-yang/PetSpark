<template>
  <section class="ai-chat-view">
    <header class="ai-chat-view__hero">
      <div class="ai-chat-view__identity">
        <div class="ai-chat-view__avatar-wrap">
          <img :src="assistantAvatar" alt="派派 AI 宠物助手头像">
          <span aria-label="在线" />
        </div>
        <div>
          <p><span>AI</span> PetSpark 宠物伙伴</p>
          <h1>和派派聊聊它的日常</h1>
          <span>提供护理信息与陪伴建议，不替代兽医诊断；紧急情况请及时联系专业机构。</span>
          <div class="ai-chat-view__trust">
            <span>日常护理</span><span>陪伴建议</span><span>隐私保护</span>
          </div>
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
            <div class="ai-chat-view__agent">
              <img :src="assistantAvatar" alt="">
              <span><strong>派派</strong><small><i class="ai-chat-view__online" />在线 · 快速回答模式</small></span>
            </div>
            <span class="ai-chat-view__boundary">回答仅供日常信息参考</span>
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
            <div class="ai-chat-view__composer-label">
              <strong>告诉派派具体情况</strong>
              <span>年龄、症状持续时间和近期变化会让建议更有参考价值</span>
            </div>
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
              <label class="ai-chat-view__stream-switch"><input v-model="streamMode" type="checkbox"> <span>逐字显示</span></label>
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
.ai-chat-view { display: grid; gap: 16px; max-width: 1320px; margin: 0 auto; position: relative; }
.ai-chat-view::before { content: ''; position: absolute; z-index: -1; inset: -32px -60px auto; height: 300px; background: radial-gradient(circle at 16% 20%, rgba(255, 185, 208, .24), transparent 31%), radial-gradient(circle at 82% 22%, rgba(146, 128, 224, .20), transparent 35%); filter: blur(8px); pointer-events: none; }
.ai-chat-view__hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 28px;
  padding: 20px 26px;
  background: linear-gradient(115deg, rgba(255, 255, 255, .97), rgba(253, 249, 255, .93) 58%, rgba(255, 245, 239, .95));
  border: 1px solid rgba(114, 95, 197, 0.16);
  border-radius: var(--ps-radius-lg);
  box-shadow: 0 16px 46px rgba(67, 52, 126, 0.10);
  backdrop-filter: blur(14px);
}
.ai-chat-view__identity { display: flex; align-items: center; gap: 18px; min-width: 0; }
.ai-chat-view__avatar-wrap { position: relative; flex: 0 0 68px; }
.ai-chat-view__avatar-wrap img { width: 68px; height: 68px; object-fit: cover; border: 3px solid #fff; border-radius: 22px; box-shadow: 0 9px 24px rgba(114, 95, 197, 0.20); }
.ai-chat-view__avatar-wrap > span { position: absolute; right: -1px; bottom: 1px; width: 13px; height: 13px; background: #5bbf75; border: 3px solid #fff; border-radius: 50%; }
.ai-chat-view__identity p { margin: 0 0 3px; color: var(--ps-color-purple); font-size: 12px; font-weight: 800; letter-spacing: .02em; }
.ai-chat-view__identity p > span { display: inline-grid; place-items: center; margin-right: 5px; padding: 2px 5px; color: #fff; background: linear-gradient(135deg, #7664ce, #d8487c); border-radius: 6px; font-size: 9px; }
.ai-chat-view__identity h1 { margin: 0; font-size: clamp(24px, 2.6vw, 32px); line-height: 1.2; letter-spacing: -0.025em; }
.ai-chat-view__identity span { display: block; max-width: 68ch; margin-top: 5px; color: var(--ps-color-muted); font-size: 13px; }
.ai-chat-view__trust { display: flex; gap: 7px; margin-top: 8px; }
.ai-chat-view__trust span { margin: 0; padding: 3px 8px; color: #756a8d; background: rgba(117, 100, 206, .08); border: 1px solid rgba(117, 100, 206, .10); border-radius: 999px; font-size: 10px; }
.ai-chat-view__toolbar { display: flex; flex: 0 0 auto; gap: 8px; }
.ai-chat-view__main {
  display: grid;
  grid-template-columns: 272px minmax(0, 1fr);
  min-height: min(650px, calc(100vh - 230px));
  overflow: hidden;
  background: rgba(255, 255, 255, 0.96);
  border: 1px solid rgba(114, 95, 197, 0.16);
  border-radius: var(--ps-radius-lg);
  box-shadow: 0 14px 38px rgba(67, 52, 126, 0.10);
  backdrop-filter: blur(16px);
}
.ai-chat-view__sessions { display: flex; flex-direction: column; min-width: 0; padding: 20px 16px; background: linear-gradient(180deg, rgba(245, 241, 255, .95), rgba(255, 248, 250, .75)); border-right: 1px solid rgba(114, 95, 197, 0.14); }
.ai-chat-view__sessions-head { display: flex; align-items: center; justify-content: space-between; padding: 0 4px 14px; }
.ai-chat-view__sessions-head span { color: var(--ps-color-muted); font-size: 12px; }
.ai-chat-view__new { width: 100%; padding: 11px 12px; color: #fff; background: linear-gradient(115deg, #6756ba, #806bd1); border: 0; border-radius: 12px; box-shadow: 0 8px 18px rgba(93, 78, 170, .20); font-weight: 700; cursor: pointer; transition: transform .18s ease, box-shadow .18s ease; }
.ai-chat-view__new:hover { transform: translateY(-1px); box-shadow: 0 11px 24px rgba(93, 78, 170, .27); }
.ai-chat-view__sessions ul { display: grid; gap: 6px; max-height: 360px; margin: 14px 0 0; padding: 0; overflow-y: auto; list-style: none; }
.ai-chat-view__session { display: grid; grid-template-columns: 30px minmax(0, 1fr) auto; align-items: center; gap: 7px; padding: 8px; border-radius: var(--ps-radius-sm); cursor: pointer; }
.ai-chat-view__session:hover { background: rgba(255, 255, 255, 0.70); }
.ai-chat-view__session.active { color: #493d8d; background: #fff; box-shadow: 0 6px 18px rgba(67, 52, 126, 0.10); }
.ai-chat-view__session-icon { display: grid; width: 28px; height: 28px; place-items: center; color: #5d4eaa; background: #ece8ff; border-radius: 9px; font-size: 11px; font-weight: 800; }
.ai-chat-view__session-name { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.ai-chat-view__empty { margin: 14px 4px; color: var(--ps-color-muted); font-size: 13px; }
.ai-chat-view__privacy { display: grid; gap: 4px; margin-top: auto; padding: 13px; color: #554b78; background: rgba(255, 255, 255, 0.78); border: 1px solid rgba(114, 95, 197, .10); border-radius: 12px; }
.ai-chat-view__privacy strong { font-size: 12px; }
.ai-chat-view__privacy span { color: var(--ps-color-muted); font-size: 11px; line-height: 1.55; }
.ai-chat-view__chat { display: flex; min-width: 0; min-height: min(650px, calc(100vh - 230px)); flex-direction: column; background: linear-gradient(180deg, #fff, #fdfcff); }
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
.ai-chat-view__chat-head { display: flex; align-items: center; justify-content: space-between; min-height: 64px; padding: 9px 20px; background: rgba(255, 255, 255, .88); border-bottom: 1px solid var(--ps-color-border); }
.ai-chat-view__agent { display: flex; align-items: center; gap: 10px; }
.ai-chat-view__agent img { width: 38px; height: 38px; border-radius: 12px; box-shadow: 0 5px 12px rgba(93, 78, 170, .16); }
.ai-chat-view__agent > span { display: grid; gap: 1px; }
.ai-chat-view__agent small { display: flex; align-items: center; gap: 6px; color: var(--ps-color-muted); font-size: 11px; font-weight: 400; }
.ai-chat-view__boundary { padding: 6px 10px; color: #776f86; background: #f8f6fc; border-radius: 999px; font-size: 11px; }
.ai-chat-view__online { display: inline-block; width: 6px; height: 6px; background: #54a968; border-radius: 50%; box-shadow: 0 0 0 3px rgba(84, 169, 104, 0.12); }
.ai-chat-view__conversation { display: flex; flex: 1; min-height: 0; flex-direction: column; padding: 24px; overflow-y: auto; background: radial-gradient(circle at 100% 0, rgba(244, 219, 228, .24), transparent 26%), radial-gradient(circle at 0 100%, rgba(221, 215, 250, .30), transparent 28%), #fcfbfe; }
.ai-chat-view__starter { display: flex; align-items: flex-start; flex-direction: column; gap: 8px; width: min(520px, 100%); margin: auto; padding: 24px; background: #faf9ff; border: 1px solid #e8e4f8; border-radius: var(--ps-radius-md); }
.ai-chat-view__starter > span { margin-bottom: 4px; color: var(--ps-color-muted); }
.ai-chat-view__messages { display: flex; flex-direction: column; gap: 12px; margin: 0; padding: 0; list-style: none; }
.ai-chat-view__message { display: grid; grid-template-columns: 34px minmax(0, auto); align-items: start; gap: 8px; max-width: 82%; }
.ai-chat-view__message.role-user { align-self: flex-end; grid-template-columns: minmax(0, auto) 34px; }
.ai-chat-view__message.role-user .ai-chat-view__role { grid-column: 2; grid-row: 1; color: #fff; background: var(--ps-color-pink); }
.ai-chat-view__message.role-user .ai-chat-view__text { grid-column: 1; grid-row: 1; color: #fff; background: #5d4eaa; border-radius: 14px 4px 14px 14px; }
.ai-chat-view__role { display: grid; width: 34px; height: 34px; place-items: center; color: #5d4eaa; background: #ece8ff; border-radius: 11px; font-size: 11px; font-weight: 800; }
.ai-chat-view__text { padding: 11px 14px; background: #fff; border: 1px solid #ece9f5; border-radius: 5px 16px 16px; box-shadow: 0 7px 20px rgba(77, 65, 124, .06); line-height: 1.7; white-space: pre-wrap; }
.ai-chat-view__composer { padding: 13px 18px 15px; background: rgba(255, 255, 255, .97); border-top: 1px solid var(--ps-color-border); box-shadow: 0 -10px 28px rgba(74, 61, 129, .035); }
.ai-chat-view__composer-label { display: flex; align-items: baseline; gap: 10px; margin-bottom: 8px; }
.ai-chat-view__composer-label strong { color: #3f384d; font-size: 12px; }
.ai-chat-view__composer-label span { color: #9a93a6; font-size: 10px; }
.ai-chat-view__composer ::v-deep .el-textarea__inner { min-height: 68px !important; padding: 12px 14px; background: #fbfafd; border-color: #e5e0f1; border-radius: 13px; resize: none; transition: border-color .2s, box-shadow .2s; }
.ai-chat-view__composer ::v-deep .el-textarea__inner:focus { border-color: #8a78d5; box-shadow: 0 0 0 3px rgba(117, 100, 206, .10); }
.ai-chat-view__composer-actions { display: flex; align-items: center; gap: 14px; margin-top: 9px; color: var(--ps-color-muted); font-size: 12px; }
.ai-chat-view__stream-switch { display: flex; align-items: center; gap: 6px; padding: 4px 8px; background: #f7f4fd; border-radius: 999px; cursor: pointer; }
.ai-chat-view__stream-switch input { accent-color: #6b59bb; }
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
