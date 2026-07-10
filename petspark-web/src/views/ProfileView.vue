<template>
  <section class="profile-view">
    <PageHeader title="个人资料" description="更新公开昵称、联系方式、头像与个人简介。" />
    <el-alert
      v-if="message && !loadError"
      :title="message"
      :type="messageType"
      show-icon
      class="message"
    />
    <LoadingState v-if="loading" text="正在读取个人资料…" />
    <ErrorState
      v-else-if="loadError"
      title="个人资料暂时无法加载"
      :description="loadError"
      @retry="load"
    />
    <el-card v-else class="profile-card">
      <el-form label-width="96px" @submit.native.prevent="save">
        <el-form-item label="用户名">
          <span>{{ form.username }}</span>
        </el-form-item>
        <el-form-item label="邮箱">
          <span>{{ form.email }}</span>
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model.trim="form.nickname" maxlength="64" show-word-limit />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model.trim="form.phone" placeholder="仅保存后返回脱敏号码" maxlength="16" />
          <p v-if="form.phoneMasked" class="hint">当前号码：{{ form.phoneMasked }}</p>
        </el-form-item>
        <el-form-item label="头像">
          <ImageUploader
            ref="avatarUploader"
            v-model="form.avatarFileId"
            business-type="PROFILE_AVATAR"
            @uploaded="message = '头像已上传，保存资料后生效'"
          />
          <p v-if="form.avatarUrl" class="hint">当前头像地址：{{ form.avatarUrl }}</p>
        </el-form-item>
        <el-form-item label="简介">
          <el-input
            v-model.trim="form.bio"
            type="textarea"
            maxlength="255"
            show-word-limit
            :rows="4"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="saving" @click="save">保存资料</el-button>
          <el-button @click="load">重新加载</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </section>
</template>

<script>
import { getMyProfile, updateMyProfile } from '@/api/users'
import PageHeader from '@/components/ui/PageHeader.vue'
import LoadingState from '@/components/ui/LoadingState.vue'
import ErrorState from '@/components/ui/ErrorState.vue'

export default {
  name: 'ProfileView',
  components: {
    ImageUploader: () => import('@/components/ImageUploader.vue'),
    PageHeader,
    LoadingState,
    ErrorState
  },
  data() {
    return {
      loading: false,
      saving: false,
      message: '',
      messageType: 'success',
      loadError: '',
      form: {
        id: '',
        username: '',
        email: '',
        nickname: '',
        phone: '',
        phoneMasked: '',
        avatarFileId: '',
        avatarUrl: '',
        bio: '',
        version: 0
      }
    }
  },
  created() {
    this.load()
  },
  methods: {
    async load() {
      this.loading = true
      this.message = ''
      this.loadError = ''
      try {
        const response = await getMyProfile()
        this.applyProfile(response.data)
      } catch (err) {
        this.loadError = err.message
      } finally {
        this.loading = false
      }
    },
    async save() {
      this.saving = true
      this.message = ''
      try {
        if (this.$refs.avatarUploader && this.form.avatarFileId) {
          await this.$refs.avatarUploader.confirm()
        }
        const response = await updateMyProfile({
          nickname: this.form.nickname,
          phone: this.form.phone,
          avatarFileId: this.form.avatarFileId,
          bio: this.form.bio,
          version: this.form.version
        })
        this.applyProfile(response.data)
        this.$store.commit('setUser', {
          ...this.$store.state.user,
          nickname: response.data.nickname
        })
        this.messageType = 'success'
        this.message = '个人资料已保存'
      } catch (err) {
        this.messageType = 'error'
        this.message = err.message
      } finally {
        this.saving = false
      }
    },
    applyProfile(profile) {
      this.form = {
        id: profile.id,
        username: profile.username,
        email: profile.email,
        nickname: profile.nickname,
        phone: '',
        phoneMasked: profile.phoneMasked || '',
        avatarFileId: profile.avatarFileId || '',
        avatarUrl: profile.avatarUrl || '',
        bio: profile.bio || '',
        version: profile.version
      }
    }
  }
}
</script>

<style scoped>
.profile-view {
  max-width: 860px;
  margin: 0 auto;
}

.profile-card {
  border: 0;
}

.message {
  margin-bottom: 16px;
}

.hint {
  margin: 6px 0 0;
  color: var(--ps-color-muted);
  font-size: 12px;
}
</style>
