<template>
  <section class="auth-card">
    <h2>注册 PetSpark</h2>
    <el-form label-position="top" @submit.native.prevent>
      <el-form-item label="用户名">
        <el-input v-model.trim="form.username" autocomplete="username" />
      </el-form-item>
      <el-form-item label="邮箱">
        <el-input v-model.trim="form.email" autocomplete="email" />
      </el-form-item>
      <el-form-item label="昵称">
        <el-input v-model.trim="form.nickname" />
      </el-form-item>
      <el-form-item label="密码">
        <el-input v-model="form.password" type="password" autocomplete="new-password" show-password />
        <p class="hint">至少 8 位，并包含大小写字母和数字。</p>
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
      <el-button type="primary" :loading="submitting" @click="submit">注册</el-button>
      <router-link class="auth-link" to="/login">已有账号？去登录</router-link>
    </el-form>
  </section>
</template>

<script>
import { issueCaptcha, register } from '@/api/auth'

export default {
  name: 'RegisterView',
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
        username: '',
        email: '',
        nickname: '',
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
        await register({
          ...this.form,
          captchaId: this.captcha.captchaId
        })
        this.$message.success('注册成功，请登录')
        this.$router.push('/login')
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
  max-width: 460px;
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

.hint {
  margin: 6px 0 0;
  color: #909399;
  font-size: 12px;
}

.auth-alert {
  margin-bottom: 16px;
}

.auth-link {
  margin-left: 16px;
}
</style>
