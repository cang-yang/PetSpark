<template>
  <section class="image-uploader">
    <button type="button" class="picker" :disabled="uploading" @click="$refs.input.click()">
      <img v-if="previewUrl" :src="previewUrl" alt="待上传图片预览">
      <span v-else>选择图片</span>
    </button>
    <input
      ref="input"
      class="native-input"
      type="file"
      accept="image/jpeg,image/png,image/webp"
      @change="selectFile"
    >
    <el-progress v-if="uploading" :percentage="progress" />
    <p class="hint">支持 JPEG、PNG、WebP，最大 5 MiB。</p>
    <el-alert v-if="error" :title="error" type="error" show-icon />
  </section>
</template>

<script>
import { confirmFile, uploadImage } from '@/api/files'

const ALLOWED_TYPES = ['image/jpeg', 'image/png', 'image/webp']
const MAX_SIZE = 5 * 1024 * 1024

export default {
  name: 'ImageUploader',
  props: {
    value: { type: String, default: '' },
    businessType: { type: String, required: true }
  },
  data() {
    return {
      previewUrl: '',
      progress: 0,
      uploading: false,
      error: ''
    }
  },
  beforeDestroy() {
    this.revokePreview()
  },
  methods: {
    async selectFile(event) {
      const file = event.target.files && event.target.files[0]
      if (!file) return
      this.error = ''
      if (!ALLOWED_TYPES.includes(file.type)) {
        this.error = '只允许 JPEG、PNG 或 WebP 图片'
        return
      }
      if (file.size > MAX_SIZE) {
        this.error = '图片不能超过 5 MiB'
        return
      }
      this.revokePreview()
      this.previewUrl = URL.createObjectURL(file)
      this.uploading = true
      this.progress = 0
      try {
        const response = await uploadImage(file, this.businessType, (progressEvent) => {
          if (progressEvent.total) {
            this.progress = Math.round((progressEvent.loaded * 100) / progressEvent.total)
          }
        })
        this.$emit('input', response.data.fileId)
        this.$emit('uploaded', response.data)
      } catch (err) {
        this.error = err.message
      } finally {
        this.uploading = false
        event.target.value = ''
      }
    },
    async confirm() {
      if (!this.value) return null
      const response = await confirmFile(this.value)
      this.$emit('confirmed', response.data)
      return response.data
    },
    revokePreview() {
      if (this.previewUrl) {
        URL.revokeObjectURL(this.previewUrl)
        this.previewUrl = ''
      }
    }
  }
}
</script>

<style scoped>
.image-uploader {
  max-width: 360px;
}

.picker {
  width: 180px;
  height: 180px;
  overflow: hidden;
  color: #606266;
  background: #fafafa;
  border: 1px dashed #c0c4cc;
  border-radius: 10px;
  cursor: pointer;
}

.picker:hover {
  color: #409eff;
  border-color: #409eff;
}

.picker img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.native-input {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
}

.hint {
  color: #909399;
  font-size: 12px;
}
</style>
