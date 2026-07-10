<template>
  <section class="ai-recommend-view">
    <header class="ai-recommend-view__head">
      <h2>AI 推荐</h2>
      <div class="ai-recommend-view__toolbar">
        <el-button v-if="consentGranted" size="small" @click="revokeConsent" data-testid="revoke-consent">撤回同意</el-button>
      </div>
    </header>

    <!-- 降级横幅：AI 未启用时显示原因（推荐仍可用，走规则兜底）。 -->
    <el-alert
      v-if="!enabled"
      :title="degradationTitle"
      type="warning"
      :closable="false"
      show-icon
      data-testid="ai-degradation-banner"
    >
      <span>AI 模型未启用，推荐结果由规则兜底排序，仅来自真实可见候选。</span>
    </el-alert>

    <!-- 未同意：引导同意。 -->
    <status-panel
      v-if="!consentGranted"
      status="empty"
      status-label="未同意 AI 服务协议"
      status-class="warning"
      reason="使用 AI 推荐前需阅读并同意协议"
      role="用户"
      next-step="点击下方按钮同意协议"
      test-id="consent-required-panel"
    >
      <template #actions>
        <el-button type="primary" @click="showConsent = true" data-testid="show-consent">同意协议</el-button>
      </template>
    </status-panel>

    <!-- 推荐表单 + 结果。 -->
    <div v-else class="ai-recommend-view__main">
      <el-form :model="form" label-width="90px" class="ai-recommend-view__form">
        <el-form-item label="宠物物种">
          <el-input v-model="form.species" placeholder="如：狗、猫" maxlength="32" data-testid="recommend-species" />
        </el-form-item>
        <el-form-item label="宠物月龄">
          <el-input-number v-model="form.age" :min="0" :max="360" data-testid="recommend-age" />
        </el-form-item>
        <el-form-item label="推荐偏好">
          <el-input
            v-model="form.preference"
            type="textarea"
            :rows="2"
            placeholder="如：活泼、温顺、适合幼犬"
            maxlength="4000"
            data-testid="recommend-preference"
          />
        </el-form-item>
        <el-form-item label="推荐对象">
          <el-select v-model="form.candidateType" placeholder="选择推荐类型" data-testid="recommend-candidate-type">
            <el-option label="宠物" value="PET" />
            <el-option label="商品" value="GOODS" />
            <el-option label="服务" value="SERVICE" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            :loading="loading"
            :disabled="!canSubmit"
            data-testid="recommend-submit"
            @click="submit"
          >获取推荐</el-button>
        </el-form-item>
      </el-form>

      <!-- 结果区。 -->
      <div v-if="loading" class="ai-recommend-view__loading" data-testid="recommend-loading">正在生成推荐…</div>
      <div v-else-if="error" class="ai-recommend-view__error" data-testid="recommend-error">
        <el-alert :title="error" type="error" :closable="false" show-icon />
      </div>
      <div v-else-if="result" class="ai-recommend-view__result" data-testid="recommend-result">
        <div v-if="!result.items.length" class="ai-recommend-view__empty" data-testid="recommend-empty">
          暂无可推荐候选，请调整偏好或推荐对象类型后重试。
        </div>
        <ul v-else class="ai-recommend-view__items" data-testid="recommend-items">
          <li
            v-for="(item, idx) in result.items"
            :key="item.id"
            class="ai-recommend-view__item"
            :data-testid="`recommend-item-${idx}`"
          >
            <div class="ai-recommend-view__item-head">
              <span class="ai-recommend-view__item-type">{{ typeLabel(item.type) }}</span>
              <span class="ai-recommend-view__item-id">{{ item.id }}</span>
            </div>
            <p class="ai-recommend-view__item-reason">{{ item.reason }}</p>
          </li>
        </ul>
        <p v-if="result.boundaryNotice" class="ai-recommend-view__boundary" data-testid="recommend-boundary">
          {{ result.boundaryNotice }}
        </p>
      </div>
    </div>

    <ai-consent-dialog
      :visible.sync="showConsent"
      @consented="onConsented"
    />
  </section>
</template>

<script>
import { getAiStatus, withdrawAiConsent, recommendAi } from '@/api/ai'
import StatusPanel from '@/components/StatusPanel.vue'
import AiConsentDialog from '@/components/AiConsentDialog.vue'

/**
 * AI 推荐视图（API-AI-007，路由 /ai/recommend）。
 *
 * 流程：先查 status → 未同意展示同意弹窗 → 同意后填写表单 → 提交后展示推荐结果。
 *
 * NFR-AI-001：所有展示项均由服务端从真实可见候选中选出并再校验，前端只负责渲染。
 * 降级：AI 未启用时服务端走规则兜底排序，前端展示降级横幅但功能仍可用。
 */
export default {
  name: 'AiRecommendView',
  components: { StatusPanel, AiConsentDialog },
  data() {
    return {
      enabled: false,
      consentGranted: false,
      degradationReason: '',
      showConsent: false,
      loading: false,
      error: '',
      result: null,
      form: {
        species: '',
        age: 0,
        preference: '',
        candidateType: 'GOODS'
      }
    }
  },
  computed: {
    degradationTitle() {
      return this.degradationReason || 'AI 服务暂未启用'
    },
    canSubmit() {
      return this.form.species.trim() !== '' && this.form.preference.trim() !== '' && this.form.candidateType
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
    async submit() {
      if (!this.canSubmit) return
      this.loading = true
      this.error = ''
      this.result = null
      try {
        const res = await recommendAi({
          species: this.form.species.trim(),
          age: this.form.age,
          preference: this.form.preference.trim(),
          candidateType: this.form.candidateType
        })
        this.result = res.data
      } catch (err) {
        this.error = err.message || '推荐请求失败'
      } finally {
        this.loading = false
      }
    },
    async onConsented() {
      this.consentGranted = true
    },
    async revokeConsent() {
      try {
        await withdrawAiConsent()
        this.consentGranted = false
        this.result = null
        this.$message.success('已撤回 AI 服务同意')
      } catch (err) {
        this.$message.error(err.message)
      }
    },
    typeLabel(type) {
      if (type === 'PET') return '宠物'
      if (type === 'GOODS') return '商品'
      if (type === 'SERVICE') return '服务'
      return type
    }
  }
}
</script>

<style scoped>
.ai-recommend-view { max-width: 800px; margin: 24px auto; padding: 24px; background: #fff; border-radius: 10px; box-shadow: 0 10px 30px rgba(36, 49, 61, 0.06); }
.ai-recommend-view__head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.ai-recommend-view__head h2 { margin: 0; }
.ai-recommend-view__main { margin-top: 16px; }
.ai-recommend-view__form { max-width: 560px; }
.ai-recommend-view__loading { padding: 40px 0; text-align: center; color: #909399; }
.ai-recommend-view__empty { padding: 32px 0; text-align: center; color: #909399; }
.ai-recommend-view__items { list-style: none; margin: 16px 0 0; padding: 0; }
.ai-recommend-view__item { padding: 12px 16px; margin-bottom: 10px; border: 1px solid #e4e7ed; border-radius: 8px; }
.ai-recommend-view__item-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px; }
.ai-recommend-view__item-type { padding: 2px 8px; border-radius: 10px; font-size: 12px; font-weight: 600; color: #fff; background: #409eff; }
.ai-recommend-view__item-id { color: #909399; font-size: 12px; }
.ai-recommend-view__item-reason { margin: 0; color: #24313d; font-size: 14px; line-height: 1.5; }
.ai-recommend-view__boundary { margin-top: 16px; padding: 8px 12px; background: #fdf6ec; border: 1px solid #faecd8; border-radius: 6px; color: #e6a23c; font-size: 13px; }
</style>
