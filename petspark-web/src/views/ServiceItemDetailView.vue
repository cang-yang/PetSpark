<template>
  <section class="service-detail-page">
    <el-card v-if="item">
      <h2>{{ item.name }}</h2>
      <p class="kind">{{ kindLabel(item.kind) }}</p>
      <p v-if="item.description">{{ item.description }}</p>
      <p class="price">基础价：￥{{ item.basePrice }}</p>
      <div v-if="item.qualification" class="transparency">
        <h4>资质说明</h4>
        <p>{{ item.qualification }}</p>
      </div>
      <div v-if="item.availabilityNote" class="transparency">
        <h4>时段说明</h4>
        <p>{{ item.availabilityNote }}</p>
      </div>
      <div v-if="item.exceptionRule" class="transparency">
        <h4>异常规则</h4>
        <p>{{ item.exceptionRule }}</p>
      </div>
    </el-card>

    <el-card v-if="item" class="booking-card" data-testid="service-booking-section">
      <h3>选择资源与窗口</h3>
      <el-form :model="form" label-width="100px">
        <el-form-item label="服务资源">
          <el-select v-model="form.resourceId" placeholder="选择资源" @change="loadSlots">
            <el-option v-for="res in resources" :key="res.id" :label="res.name" :value="res.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="可用窗口">
          <el-select v-model="form.slotId" placeholder="选择窗口">
            <el-option
              v-for="slot in slots"
              :key="slot.id"
              :label="formatSlot(slot)"
              :value="slot.id"
              :disabled="slot.status !== 'OPEN' || slot.bookedCount >= slot.capacity" />
          </el-select>
        </el-form-item>
        <el-form-item label="宠物">
          <el-input v-model="form.petId" placeholder="宠物 ID（可选）" />
        </el-form-item>
        <el-form-item label="联系人"><el-input v-model="form.customerName" /></el-form-item>
        <el-form-item label="手机号"><el-input v-model="form.customerPhone" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <div class="booking-actions">
        <el-button type="primary" :loading="submitting" @click="submit">提交预约</el-button>
      </div>
    </el-card>
  </section>
</template>

<script>
import { getServiceItem, listServiceResources, listServiceSlots, createServiceBooking } from '@/api/service'

export default {
  name: 'ServiceItemDetailView',
  data() {
    return {
      item: null,
      resources: [],
      slots: [],
      form: { resourceId: undefined, slotId: undefined, petId: undefined, customerName: '', customerPhone: '', remark: '' },
      submitting: false
    }
  },
  created() {
    this.loadItem()
    this.loadResources()
  },
  methods: {
    async loadItem() {
      try {
        const response = await getServiceItem(this.$route.params.id)
        this.item = response.data
      } catch (error) {
        this.$message && this.$message.error(error.message)
      }
    },
    async loadResources() {
      try {
        const response = await listServiceResources({ serviceItemId: this.$route.params.id, page: 1, size: 100 })
        this.resources = response.data.items || []
      } catch (error) {
        this.$message && this.$message.error(error.message)
      }
    },
    async loadSlots() {
      if (!this.form.resourceId) {
        this.slots = []
        return
      }
      try {
        const response = await listServiceSlots({ resourceId: this.form.resourceId, page: 1, size: 100 })
        this.slots = response.data.items || []
      } catch (error) {
        this.$message && this.$message.error(error.message)
      }
    },
    async submit() {
      if (!this.form.resourceId || !this.form.slotId || !this.form.customerName || !this.form.customerPhone) {
        this.$message && this.$message.warning('请选择资源、窗口并填写联系人与手机号')
        return
      }
      this.submitting = true
      try {
        const payload = {
          serviceItemId: this.$route.params.id,
          resourceId: this.form.resourceId,
          slotId: this.form.slotId,
          petId: this.form.petId || undefined,
          customerName: this.form.customerName,
          customerPhone: this.form.customerPhone,
          remark: this.form.remark || undefined
        }
        const response = await createServiceBooking(payload)
        this.$message && this.$message.success('预约已创建：' + response.data.bookingNo)
        this.$router && this.$router.push('/my/services/bookings')
      } catch (error) {
        this.$message && this.$message.error(error.message)
      } finally {
        this.submitting = false
      }
    },
    kindLabel(kind) {
      const labels = { GENERIC: '通用服务', TRAINING: '训练', BEAUTY: '美容', MEDICAL: '医疗' }
      return labels[kind] || kind || ''
    },
    formatSlot(slot) {
      const start = this.formatTime(slot.startAt)
      const end = this.formatTime(slot.endAt)
      const left = (slot.capacity || 0) - (slot.bookedCount || 0)
      return `${start} ~ ${end}（余 ${left}）`
    },
    formatTime(value) {
      if (!value) return ''
      const date = typeof value === 'string' ? new Date(value) : value
      if (Number.isNaN(date.getTime())) return String(value)
      const pad = (n) => String(n).padStart(2, '0')
      return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
    }
  }
}
</script>

<style scoped>
.kind {
  color: #909399;
}
.price {
  color: #f56c6c;
  font-size: 18px;
  font-weight: 700;
}
.transparency {
  margin-top: 12px;
  padding: 12px;
  background: #f5f7fa;
  border-radius: 6px;
}
.transparency h4 {
  margin: 0 0 6px 0;
  color: #303133;
}
.booking-card {
  margin-top: 16px;
}
.booking-actions {
  display: flex;
  gap: 12px;
  margin-top: 12px;
}
</style>
