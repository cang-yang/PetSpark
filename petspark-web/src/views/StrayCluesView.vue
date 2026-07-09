<template>
  <section class="stray-clues">
    <h2 data-testid="stray-submit-title">提交流浪救助线索</h2>
    <p class="intro">发现疑似走失、受伤或需要帮助的动物时，请留下位置和现场描述，后台会跟进受理。</p>

    <el-form label-width="96px" class="clue-form" data-testid="stray-submit-form">
      <el-form-item label="动物类型" required>
        <el-select v-model="form.animalType" placeholder="请选择">
          <el-option label="狗" value="DOG" />
          <el-option label="猫" value="CAT" />
          <el-option label="其他" value="OTHER" />
        </el-select>
      </el-form-item>
      <el-form-item label="发现位置" required>
        <el-input v-model="form.location" maxlength="255" show-word-limit placeholder="例如：东门花坛、3号楼车棚" />
      </el-form-item>
      <el-form-item label="现场描述" required>
        <el-input v-model="form.description" type="textarea" :rows="5" maxlength="1000" show-word-limit placeholder="请描述动物状态、是否受伤、是否可接近等" />
      </el-form-item>
      <el-form-item label="联系电话">
        <el-input v-model="form.contactPhone" maxlength="32" placeholder="选填，便于救助员联系核实" />
      </el-form-item>
      <el-form-item label="现场图片">
        <div class="image-grid">
          <ImageUploader
            v-for="slot in imageSlots"
            :key="slot.index"
            ref="uploaders"
            v-model="slot.fileId"
            business-type="STRAY_CLUE"
            @uploaded="onUploaded(slot, $event)"
          />
        </div>
        <p class="hint">最多 3 张；提交前会确认图片归属，后台可在详情中查看。</p>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="submitting" data-testid="stray-submit-button" @click="submit">提交线索</el-button>
        <el-button @click="resetForm">重置</el-button>
      </el-form-item>
    </el-form>

    <el-alert v-if="created" type="success" show-icon :closable="false" data-testid="stray-submit-success">
      <template slot="title">线索 {{ created.clueNo }} 已提交，当前状态：{{ created.statusLabel || statusLabel(created.status) }}</template>
    </el-alert>
  </section>
</template>

<script>
import ImageUploader from '@/components/ImageUploader.vue'
import { createStrayClue } from '@/api/stray'

export default {
  name: 'StrayCluesView',
  components: { ImageUploader },
  data() {
    return {
      form: { animalType: 'CAT', location: '', description: '', contactPhone: '' },
      imageSlots: [{ index: 0, fileId: '' }, { index: 1, fileId: '' }, { index: 2, fileId: '' }],
      created: null,
      submitting: false
    }
  },
  methods: {
    onUploaded(slot, file) {
      slot.fileId = file && file.fileId ? file.fileId : slot.fileId
    },
    validate() {
      if (!this.form.animalType) return '请选择动物类型'
      if (!this.form.location || !this.form.location.trim()) return '请填写发现位置'
      if (!this.form.description || !this.form.description.trim()) return '请填写现场描述'
      return ''
    },
    async confirmImages() {
      const uploaders = Array.isArray(this.$refs.uploaders) ? this.$refs.uploaders : []
      const confirmed = []
      for (const uploader of uploaders) {
        if (uploader && uploader.value) {
          const file = await uploader.confirm()
          if (file && file.fileId) confirmed.push(file.fileId)
        }
      }
      return confirmed
    },
    async submit() {
      const warning = this.validate()
      if (warning) {
        this.$message && this.$message.warning(warning)
        return
      }
      this.submitting = true
      try {
        const imageFileIds = await this.confirmImages()
        const payload = {
          animalType: this.form.animalType,
          location: this.form.location.trim(),
          description: this.form.description.trim(),
          contactPhone: this.form.contactPhone ? this.form.contactPhone.trim() : undefined,
          imageFileIds
        }
        const idemKey = `stray-${Date.now()}-${Math.random().toString(16).slice(2)}`
        const response = await createStrayClue(payload, idemKey)
        this.created = response.data
        this.$message && this.$message.success('救助线索已提交')
        this.resetForm(false)
      } catch (error) {
        this.$message && this.$message.error(error.message)
      } finally {
        this.submitting = false
      }
    },
    resetForm(clearCreated = true) {
      this.form = { animalType: 'CAT', location: '', description: '', contactPhone: '' }
      this.imageSlots = [{ index: 0, fileId: '' }, { index: 1, fileId: '' }, { index: 2, fileId: '' }]
      if (clearCreated) this.created = null
    },
    statusLabel(status) {
      const labels = { SUBMITTED: '待受理', ASSIGNED: '已指派', IN_RESCUE: '救助中', RESOLVED: '已解决', CLOSED: '已关闭' }
      return labels[status] || status
    }
  }
}
</script>

<style scoped>
.stray-clues { max-width: 900px; margin: 24px auto; }
.intro { color: #606266; margin-bottom: 20px; }
.clue-form { max-width: 760px; }
.image-grid { display: flex; gap: 16px; flex-wrap: wrap; }
.hint { color: #909399; font-size: 12px; margin: 8px 0 0; }
</style>
