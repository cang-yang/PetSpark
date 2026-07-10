<template>
  <el-dialog
    class="ps-confirm-action-dialog"
    :title="title"
    :visible="visible"
    :width="width"
    :close-on-click-modal="false"
    @close="cancel"
  >
    <div class="ps-confirm-action-dialog__content">
      <span class="ps-confirm-action-dialog__mark" aria-hidden="true">!</span>
      <div>
        <p>{{ description }}</p>
        <p v-if="warning" class="ps-confirm-action-dialog__warning">{{ warning }}</p>
      </div>
    </div>
    <el-input
      v-if="requireReason"
      v-model="reason"
      type="textarea"
      :rows="3"
      :placeholder="reasonPlaceholder"
      maxlength="255"
      show-word-limit
      data-testid="confirm-action-reason"
    />
    <span slot="footer">
      <el-button :disabled="loading" @click="cancel">{{ cancelText }}</el-button>
      <el-button :type="confirmType" :loading="loading" @click="confirm">
        {{ confirmText }}
      </el-button>
    </span>
  </el-dialog>
</template>

<script>
export default {
  name: 'ConfirmActionDialog',
  props: {
    visible: { type: Boolean, default: false },
    title: { type: String, default: '确认操作' },
    description: { type: String, required: true },
    warning: { type: String, default: '' },
    confirmText: { type: String, default: '确认' },
    cancelText: { type: String, default: '返回' },
    confirmType: { type: String, default: 'danger' },
    requireReason: { type: Boolean, default: false },
    reasonPlaceholder: { type: String, default: '请填写操作原因' },
    loading: { type: Boolean, default: false },
    width: { type: String, default: '440px' }
  },
  data() {
    return { reason: '' }
  },
  watch: {
    visible(value) {
      if (value) this.reason = ''
    }
  },
  methods: {
    cancel() {
      this.$emit('update:visible', false)
      this.$emit('cancel')
    },
    confirm() {
      const reason = this.reason.trim()
      if (this.requireReason && !reason) {
        if (this.$message) this.$message.warning(this.reasonPlaceholder)
        return
      }
      this.$emit('confirm', reason)
      this.$emit('update:visible', false)
    }
  }
}
</script>

<style scoped>
.ps-confirm-action-dialog__content {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 16px;
}

.ps-confirm-action-dialog__mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 28px;
  width: 28px;
  height: 28px;
  color: #fff;
  background: var(--ps-color-danger);
  border-radius: 50%;
  font-weight: 800;
}

p {
  margin: 2px 0 0;
  color: var(--ps-color-text);
  line-height: 1.6;
}

.ps-confirm-action-dialog__warning {
  margin-top: 6px;
  color: var(--ps-color-danger);
  font-size: 13px;
}
</style>
