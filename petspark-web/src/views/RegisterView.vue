<template>
  <AuthPanel
    title="创建你的 PetSpark 账号"
    description="从第一份宠物档案开始，把日常照顾安排得更清楚。"
    :image-src="loginDog"
    image-alt="阳光下安静陪伴主人的狗狗"
  >
    <el-form class="auth-form" label-position="top" @submit.native.prevent>
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
      <div class="auth-actions">
        <el-button type="primary" :loading="submitting" @click="submit">注册</el-button>
        <router-link class="auth-link" to="/login">已有账号？去登录</router-link>
      </div>
    </el-form>
  </AuthPanel>
</template>

<script>
import { issueCaptcha, register } from '@/api/auth'
import AuthPanel from '@/components/ui/AuthPanel.vue'
import loginDog from '@/assets/illustrations/login-dog.jpg'

export default {
  name: 'RegisterView',
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
        await register({
          ...this.form,
          captchaId: this.captcha.captchaId
        })
        this.$message.success('注册成功，请登录')
        this.$router.push('/login')
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

.hint {
  margin: 6px 0 0;
  color: var(--ps-color-muted);
  font-size: 12px;
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
