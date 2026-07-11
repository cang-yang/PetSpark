<template>
  <AuthPanel
    title="欢迎回来"
    description="登录后继续管理宠物档案、预约与订单。"
    :image-src="loginDog"
    image-alt="阳光下安静陪伴主人的狗狗"
  >
    <el-form class="auth-form" label-position="top" @submit.native.prevent>
      <el-form-item label="用户名或邮箱">
        <el-input v-model.trim="form.principal" autocomplete="username" placeholder="请输入用户名或邮箱" />
      </el-form-item>
      <el-form-item label="密码">
        <el-input v-model="form.password" type="password" autocomplete="current-password" show-password />
      </el-form-item>
      <el-form-item label="验证码">
        <div class="captcha-line">
          <el-input v-model.trim="form.captchaAnswer" placeholder="请输入答案" />
          <el-button :loading="captchaLoading" @click="loadCaptcha">
            {{ captcha.challengeText || '获取验证码' }}
          </el-button>
        </div>
      </el-form-item>
      <el-alert
        v-if="error"
        :title="error"
        type="error"
        show-icon
        class="auth-alert"
      />
      <div class="auth-actions">
        <el-button type="primary" :loading="submitting" @click="submit">登录</el-button>
        <router-link class="auth-link" to="/register">还没有账号？去注册</router-link>
        <router-link class="auth-link" to="/forgot-password">忘记密码</router-link>
      </div>
    </el-form>
  </AuthPanel>
</template>

<script>
import { issueCaptcha, login } from '@/api/auth'
import AuthPanel from '@/components/ui/AuthPanel.vue'
import loginDog from '@/assets/illustrations/login-dog.jpg'

export default {
  name: 'LoginView',
  components: { AuthPanel },
  data() {
    return {
      loginDog,
      captchaLoading: false,
      submitting: false,
      error: '',
      captcha: {
        captchaId: '',
        challengeText: ''
      },
      form: {
        principal: '',
        password: '',
        captchaAnswer: ''
      }
    }
  },
  created() {
    this.loadCaptcha()
  },
  methods: {
    async loadCaptcha(preserveError = false) {
      this.captchaLoading = true
      if (!preserveError) this.error = ''
      try {
        const res = await issueCaptcha(this.clientHash())
        this.captcha = res.data
        this.form.captchaAnswer = ''
      } catch (err) {
        if (!preserveError) this.error = '验证码暂时不可用，请稍后重试'
      } finally {
        this.captchaLoading = false
      }
    },
    async submit() {
      this.submitting = true
      this.error = ''
      try {
        const res = await login({
          ...this.form,
          captchaId: this.captcha.captchaId
        })
        await this.$store.dispatch('saveLogin', res.data)
        this.$message.success('登录成功')
        const redirect = this.$route && this.$route.query ? this.$route.query.redirect : ''
        this.$router.push(typeof redirect === 'string' && redirect.startsWith('/') && !redirect.startsWith('//') ? redirect : '/')
      } catch (err) {
        this.error = err.message
        await this.loadCaptcha(true)
      } finally {
        this.submitting = false
      }
    },
    clientHash() {
      return `web-${window.navigator.userAgent.slice(0, 64)}`
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

.auth-alert {
  margin-bottom: 16px;
}
.auth-actions { display: flex; flex-wrap: wrap; align-items: center; gap: 12px 16px; }
.auth-actions .el-button { min-width: 108px; }
.auth-link { color: var(--ps-color-muted); font-size: 13px; }
@media (max-width: 460px) {
  .auth-actions .el-button { width: 100%; }
}
</style>
