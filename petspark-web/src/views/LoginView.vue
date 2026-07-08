<template>
  <section class="auth-card">
    <h2>登录 PetSpark</h2>
    <el-form label-position="top" @submit.native.prevent>
      <el-form-item label="用户名或邮箱">
        <el-input v-model.trim="form.principal" autocomplete="username" />
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
      <el-button type="primary" :loading="submitting" @click="submit">登录</el-button>
      <router-link class="auth-link" to="/register">还没有账号？去注册</router-link>
    </el-form>
  </section>
</template>

<script>
import { issueCaptcha, login } from '@/api/auth'

export default {
  name: 'LoginView',
  data() {
    return {
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
    async loadCaptcha() {
      this.captchaLoading = true
      this.error = ''
      try {
        const res = await issueCaptcha(this.clientHash())
        this.captcha = res.data
        this.form.captchaAnswer = ''
      } catch (err) {
        this.error = err.message
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
        this.$router.push('/')
      } catch (err) {
        this.error = err.message
        this.loadCaptcha()
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
.auth-card {
  max-width: 420px;
  margin: 32px auto;
  padding: 28px;
  background: #fff;
  border-radius: 10px;
  box-shadow: 0 10px 30px rgba(36, 49, 61, 0.08);
}

.captcha-line {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 12px;
}

.auth-alert {
  margin-bottom: 16px;
}

.auth-link {
  margin-left: 16px;
}
</style>
