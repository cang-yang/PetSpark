<template>
  <AuthPanel
    title="找回访问权限"
    description="验证注册邮箱后，设置一组新的安全密码。"
    :image-src="loginDog"
    image-alt="阳光下安静陪伴主人的狗狗"
  >
    <el-steps :active="step" finish-status="success" simple>
      <el-step title="验证邮箱" />
      <el-step title="设置新密码" />
    </el-steps>

    <el-form v-if="step === 0" label-position="top" @submit.native.prevent>
      <el-form-item label="注册邮箱">
        <el-input v-model.trim="email" autocomplete="email" />
      </el-form-item>
      <el-form-item label="图形验证码">
        <div class="captcha-line">
          <el-input v-model.trim="captchaAnswer" placeholder="请输入答案" />
          <el-button :loading="captchaLoading" @click="loadCaptcha">
            {{ captcha.challengeText || '获取验证码' }}
          </el-button>
        </div>
      </el-form-item>
      <el-button type="primary" :loading="submitting" @click="requestCode">发送邮箱验证码</el-button>
    </el-form>

    <el-form v-else label-position="top" @submit.native.prevent>
      <el-form-item label="邮箱验证码">
        <el-input v-model.trim="code" maxlength="6" />
      </el-form-item>
      <el-form-item label="新密码">
        <el-input v-model="newPassword" type="password" autocomplete="new-password" show-password />
        <p class="hint">至少 8 位，并包含大小写字母和数字。</p>
      </el-form-item>
      <el-button type="primary" :loading="submitting" @click="reset">确认重置</el-button>
    </el-form>

    <el-alert v-if="error" :title="error" type="error" show-icon class="auth-alert" />
    <router-link class="back-link" to="/login">返回登录</router-link>
  </AuthPanel>
</template>

<script>
import { issueCaptcha, requestPasswordResetCode, resetPassword } from '@/api/auth'
import AuthPanel from '@/components/ui/AuthPanel.vue'
import loginDog from '@/assets/illustrations/login-dog.jpg'

export default {
  name: 'ForgotPasswordView',
  components: { AuthPanel },
  data() {
    return {
      loginDog,
      step: 0,
      email: '',
      captchaAnswer: '',
      code: '',
      newPassword: '',
      error: '',
      submitting: false,
      captchaLoading: false,
      captcha: { captchaId: '', challengeText: '' }
    }
  },
  created() {
    this.loadCaptcha()
  },
  methods: {
    async loadCaptcha() {
      this.captchaLoading = true
      try {
        const response = await issueCaptcha(`web-reset-${window.navigator.userAgent.slice(0, 56)}`)
        this.captcha = response.data
        this.captchaAnswer = ''
      } catch (err) {
        this.error = '验证码暂时不可用，请稍后重试'
      } finally {
        this.captchaLoading = false
      }
    },
    async requestCode() {
      this.submitting = true
      this.error = ''
      try {
        await requestPasswordResetCode({
          email: this.email,
          captchaId: this.captcha.captchaId,
          captchaAnswer: this.captchaAnswer
        })
        this.$message.success('如果邮箱已注册，验证码将发送到该邮箱')
        this.step = 1
      } catch (err) {
        this.error = err.message
        await this.loadCaptcha()
      } finally {
        this.submitting = false
      }
    },
    async reset() {
      this.submitting = true
      this.error = ''
      try {
        await resetPassword({ email: this.email, code: this.code, newPassword: this.newPassword })
        this.$message.success('密码已重置，请重新登录')
        this.$router.push('/login')
      } catch (err) {
        this.error = err.message
      } finally {
        this.submitting = false
      }
    }
  }
}
</script>

<style scoped>
.captcha-line {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 12px;
}

.hint {
  margin: 6px 0 0;
  color: var(--ps-color-muted);
  font-size: 12px;
}

.auth-alert,
.back-link {
  margin-top: 16px;
}

.back-link {
  display: inline-block;
  color: var(--ps-color-muted);
}
</style>
