<template>
  <section class="care-qa-view">
    <header class="care-qa-view__head">
      <h2>护理问答</h2>
      <div class="care-qa-view__toolbar">
        <el-button v-if="consentGranted" size="small" @click="revokeConsent" data-testid="revoke-consent">撤回同意</el-button>
        <el-button size="small" type="primary" :disabled="!canCreate" @click="openCreate" data-testid="new-conversation">新会话</el-button>
      </div>
    </header>

    <!-- 降级横幅：护理问答场景未开放（独立开关默认关闭）。 -->
    <el-alert
      v-if="!careQaEnabled"
      :title="degradationTitle"
      type="warning"
      :closable="false"
      show-icon
      data-testid="care-qa-degradation-banner"
    />

    <!-- 未同意：引导同意。 -->
    <status-panel
      v-else-if="!consentGranted"
      status="empty"
      status-label="未同意 AI 服务协议"
      status-class="warning"
      reason="使用护理问答前需阅读并同意协议"
      role="用户"
      next-step="点击下方按钮同意协议"
      test-id="consent-required-panel"
    >
      <template #actions>
        <el-button type="primary" @click="showConsent = true" data-testid="show-consent">同意协议</el-button>
      </template>
    </status-panel>

    <!-- 主面板：会话列表 + 护理问答区。 -->
    <div v-else class="care-qa-view__main">
      <aside class="care-qa-view__sessions">
        <el-button size="small" @click="openCreate" data-testid="new-conversation-aside">+ 新会话</el-button>
        <ul v-if="conversations.length">
          <li
            v-for="conv in conversations"
            :key="conv.id"
            :class="['care-qa-view__session', { active: conv.id === currentId }]"
            :data-testid="`session-${conv.id}`"
            @click="selectConversation(conv.id)"
          >
            <span>{{ conv.title || '护理问答' }}</span>
            <el-button
              size="mini"
              type="text"
              :loading="deletingId === conv.id"
              :data-testid="`delete-session-${conv.id}`"
              @click.stop="deleteConversation(conv.id)"
            >删除</el-button>
          </li>
        </ul>
        <p v-else class="care-qa-view__empty">暂无会话，点上方按钮开始</p>
      </aside>

      <div class="care-qa-view__chat">
        <div v-if="!currentId" class="care-qa-view__placeholder" data-testid="no-conversation">选择或创建一个会话开始护理问答</div>
        <template v-else>
          <ul class="care-qa-view__messages" data-testid="message-list">
            <li
              v-for="msg in messages"
              :key="msg.id"
              :class="['care-qa-view__message', `role-${msg.role}`]"
              :data-testid="`message-${msg.id}`"
            >
              <template v-if="msg.role === 'user'">
                <span class="care-qa-view__role">我</span>
                <span class="care-qa-view__text">{{ msg.content }}</span>
              </template>
              <template v-else>
                <div class="care-qa-reply">
                  <div class="care-qa-reply__head">
                    <el-tag :type="riskTagType(msg.payload.riskLevel)" size="small" :data-testid="`risk-tag-${msg.id}`">
                      {{ riskLabel(msg.payload.riskLevel) }}
                    </el-tag>
                  </div>
                  <div class="care-qa-reply__section" v-if="msg.payload.generalAdvice && msg.payload.generalAdvice.length">
                    <h4>护理建议</h4>
                    <ul><li v-for="(a, i) in msg.payload.generalAdvice" :key="i">{{ a }}</li></ul>
                  </div>
                  <div class="care-qa-reply__section" v-if="msg.payload.warningSigns && msg.payload.warningSigns.length">
                    <h4>需关注的迹象</h4>
                    <ul><li v-for="(w, i) in msg.payload.warningSigns" :key="i">{{ w }}</li></ul>
                  </div>
                  <div class="care-qa-reply__seekhelp" :class="{ urgent: msg.payload.riskLevel === 'URGENT' }" :data-testid="`seek-help-${msg.id}`">
                    <strong>就医建议：</strong>{{ msg.payload.seekHelp }}
                  </div>
                  <div class="care-qa-reply__disclaimer" :data-testid="`disclaimer-${msg.id}`">{{ msg.payload.disclaimer }}</div>
                </div>
              </template>
            </li>
          </ul>

          <div class="care-qa-view__input">
            <el-input
              v-model="text"
              type="textarea"
              :rows="2"
              placeholder="描述宠物的护理情况…"
              :disabled="sending"
              data-testid="message-input"
            />
            <el-button
              type="primary"
              :loading="sending"
              :disabled="!text.trim() || !careQaEnabled"
              data-testid="send-message"
              @click="send"
            >发送</el-button>
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
  getCareQaStatus,
  grantAiConsent,
  withdrawAiConsent,
  createAiConversation,
  sendCareQaMessage,
  deleteAiConversation,
  listAiMessages
} from '@/api/ai'
import StatusPanel from '@/components/StatusPanel.vue'
import AiConsentDialog from '@/components/AiConsentDialog.vue'

/**
 * 护理问答视图（PR-AI-04 / REQ-AI-004 / US-041，路由 /ai/care-qa）。
 *
 * 与 PET_CHAT 对话视图的差异：
 * - 独立场景开关（petspark.ai.care-qa.enabled）默认关闭，关闭时显示降级横幅并禁用创建/发送；
 * - 会话 scene=CARE_QA；
 * - 发送走 sendCareQaMessage，返回结构化 CareQaReplyView（riskLevel/generalAdvice/warningSigns/seekHelp/disclaimer）；
 * - 按风险等级渲染颜色与求助入口，URGENT 时突出就医建议；
 * - 固定的非诊断声明始终可见（来自服务端，模型无法抑制）。
 *
 * 安全：前端只渲染服务端已校验过的结构化结果；任何模型违规在服务端已降级为固定 URGENT 兜底。
 */
export default {
  name: 'AiCareQaView',
  components: { StatusPanel, AiConsentDialog },
  data() {
    return {
      globalEnabled: false,
      careQaEnabled: false,
      consentGranted: false,
      degradationReason: '',
      conversations: [],
      currentId: '',
      messages: [],
      text: '',
      sending: false,
      deletingId: '',
      showConsent: false
    }
  },
  computed: {
    canCreate() {
      return this.careQaEnabled && this.consentGranted
    },
    degradationTitle() {
      return this.degradationReason || (this.careQaEnabled ? 'AI 服务暂未启用' : '护理问答场景暂未开放')
    }
  },
  created() {
    this.refreshStatus()
  },
  methods: {
    async refreshStatus() {
      try {
        const [globalRes, careQaRes] = await Promise.all([getAiStatus(), getCareQaStatus()])
        const global = globalRes.data
        const care = careQaRes.data
        this.globalEnabled = !!global.enabled
        this.consentGranted = !!global.consentGranted
        this.degradationReason = global.degradationReason || ''
        // 护理问答可用 = 全局 AI 可用 且 独立开关打开
        this.careQaEnabled = !!care.enabled && !!global.enabled
      } catch (err) {
        this.$message.error(err.message)
      }
    },
    openCreate() {
      if (!this.careQaEnabled) {
        this.$message.warning('护理问答场景暂未开放')
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
        const res = await createAiConversation({ scene: 'CARE_QA', title: `护理问答 ${this.conversations.length + 1}` })
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
        const list = res.data || []
        // 历史消息：assistant 内容为 payload JSON 字符串，解析为结构化渲染。
        this.messages = list.map((m) => {
          if (m.role === 'assistant') {
            let payload = {}
            try { payload = JSON.parse(m.content) } catch (_) { payload = { generalAdvice: [m.content], warningSigns: [], seekHelp: '', riskLevel: 'GENERAL', disclaimer: '' } }
            return { id: m.id, role: 'assistant', payload }
          }
          return { id: m.id, role: 'user', content: m.content }
        })
      } catch (err) {
        this.$message.error(err.message)
      }
    },
    async send() {
      if (!this.text.trim()) return
      this.sending = true
      const convId = this.currentId
      const content = this.text
      this.messages.push({ id: `local-${Date.now()}`, role: 'user', content })
      this.text = ''
      try {
        const res = await sendCareQaMessage(convId, content)
        const data = res.data
        this.messages.push({
          id: data.requestId,
          role: 'assistant',
          payload: {
            riskLevel: data.riskLevel,
            generalAdvice: data.generalAdvice || [],
            warningSigns: data.warningSigns || [],
            seekHelp: data.seekHelp || '',
            disclaimer: data.disclaimer || ''
          }
        })
      } catch (err) {
        this.$message.error(err.message)
      } finally {
        this.sending = false
      }
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
    riskLabel(risk) {
      if (risk === 'URGENT') return '紧急'
      if (risk === 'ATTENTION') return '需关注'
      return '一般观察'
    },
    riskTagType(risk) {
      if (risk === 'URGENT') return 'danger'
      if (risk === 'ATTENTION') return 'warning'
      return 'success'
    }
  }
}
</script>

<style scoped>
.care-qa-view { max-width: 1000px; margin: 24px auto; padding: 24px; background: #fff; border-radius: 10px; box-shadow: 0 10px 30px rgba(36, 49, 61, 0.06); }
.care-qa-view__head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.care-qa-view__head h2 { margin: 0; }
.care-qa-view__main { display: grid; grid-template-columns: 220px 1fr; gap: 16px; margin-top: 16px; }
.care-qa-view__sessions ul { list-style: none; margin: 12px 0 0; padding: 0; }
.care-qa-view__session { display: flex; justify-content: space-between; align-items: center; padding: 8px 10px; border-radius: 6px; cursor: pointer; }
.care-qa-view__session.active { background: #ecf5ff; color: #409eff; }
.care-qa-view__empty { color: #909399; font-size: 13px; }
.care-qa-view__chat { display: flex; flex-direction: column; min-height: 360px; }
.care-qa-view__placeholder { color: #909399; padding: 40px 0; text-align: center; }
.care-qa-view__messages { list-style: none; margin: 0; padding: 0; flex: 1; overflow-y: auto; }
.care-qa-view__message { padding: 8px 12px; margin-bottom: 8px; border-radius: 8px; }
.care-qa-view__message.role-user { background: #f0f9ff; text-align: right; }
.care-qa-view__message.role-assistant { background: #f5f7fa; }
.care-qa-view__role { font-size: 12px; color: #909399; margin-right: 6px; }
.care-qa-view__input { display: flex; gap: 8px; margin-top: 12px; }
.care-qa-reply { text-align: left; }
.care-qa-reply__head { margin-bottom: 8px; }
.care-qa-reply__section { margin: 8px 0; }
.care-qa-reply__section h4 { margin: 0 0 4px; font-size: 13px; color: #606266; }
.care-qa-reply__section ul { margin: 0; padding-left: 20px; }
.care-qa-reply__seekhelp { margin: 8px 0; padding: 8px 10px; background: #fdf6ec; border-radius: 6px; font-size: 14px; }
.care-qa-reply__seekhelp.urgent { background: #fef0f0; color: #f56c6c; font-weight: 600; }
.care-qa-reply__disclaimer { margin-top: 8px; padding: 6px 10px; background: #f4f4f5; border-radius: 6px; font-size: 12px; color: #909399; }
</style>
