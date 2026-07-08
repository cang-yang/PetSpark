<template>
  <el-dialog
    :visible.sync="visible"
    title="AI 服务协议"
    width="560px"
    :close-on-click-modal="false"
    data-testid="ai-consent-dialog"
    @close="onClose"
  >
    <div class="ai-consent__body">
      <p>使用 AI 对话功能前，请阅读并同意<a href="/docs/ai-policy" target="_blank">《AI 服务协议》</a>：</p>
      <ul>
        <li>AI 为拟人化陪伴，<strong>不构成兽医诊断</strong>，紧急情况请就医；</li>
        <li>请勿输入手机号、身份证、地址等敏感信息，系统会自动脱敏；</li>
        <li>对话内容加密存储，调用记录仅保留输入哈希用于审计；</li>
        <li>可随时撤回同意，撤回后将无法发起新会话。</li>
      </ul>
      <el-form label-width="80px">
        <el-form-item label="策略版本">
          <el-input :value="policyVersion" disabled />
        </el-form-item>
        <el-form-item label="授权范围">
          <el-input :value="scopes" disabled />
        </el-form-item>
      </el-form>
    </div>
    <div slot="footer">
      <el-button data-testid="ai-consent-cancel" @click="onClose">取消</el-button>
      <el-button
        type="primary"
        :loading="submitting"
        data-testid="ai-consent-agree"
        @click="agree"
      >同意并开启</el-button>
    </div>
  </el-dialog>
</template>

<script>
import { grantAiConsent } from '@/api/ai'

/**
 * AI 服务协议同意弹窗。PUT /ai/consent 后通知父组件继续后续操作；取消则关闭。
 */
export default {
  name: 'AiConsentDialog',
  props: {
    visible: { type: Boolean, default: false },
    policyVersion: { type: String, default: 'v1' },
    scopes: { type: String, default: 'PET_CHAT,CARE_QA,RECOMMENDATION' }
  },
  data() {
    return { submitting: false }
  },
  methods: {
    async agree() {
      this.submitting = true
      try {
        await grantAiConsent({ policyVersion: this.policyVersion, scopes: this.scopes })
        this.$emit('consented')
        this.onClose()
      } catch (err) {
        this.$message.error(err.message)
      } finally {
        this.submitting = false
      }
    },
    onClose() {
      this.$emit('update:visible', false)
      this.$emit('cancel')
    }
  }
}
</script>

<style scoped>
.ai-consent__body ul {
  margin: 8px 0 16px;
  padding-left: 20px;
  color: #606266;
  line-height: 1.6;
}
</style>