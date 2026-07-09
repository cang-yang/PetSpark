<template>
  <section class="medical-detail-page">
    <el-card v-if="item">
      <h2>{{ item.name }}</h2>
      <p v-if="item.description">{{ item.description }}</p>
      <p class="price">基础价：￥{{ item.basePrice }}</p>
      <div class="rules">
        <p v-if="profile.supportedPetTypes"><strong>适用宠物：</strong>{{ profile.supportedPetTypes }}</p>
        <p v-if="profile.clinicLicense"><strong>诊疗许可：</strong>{{ profile.clinicLicense }}</p>
        <p v-if="profile.veterinarianTeam"><strong>兽医团队：</strong>{{ profile.veterinarianTeam }}</p>
        <p v-if="profile.careScope"><strong>诊疗范围：</strong>{{ profile.careScope }}</p>
        <p v-if="profile.appointmentNotice"><strong>预约须知：</strong>{{ profile.appointmentNotice }}</p>
      </div>
      <div class="transparency">
        <p v-if="item.qualification"><strong>资质：</strong>{{ item.qualification }}</p>
        <p v-if="item.availabilityNote"><strong>时段：</strong>{{ item.availabilityNote }}</p>
        <p v-if="item.exceptionRule"><strong>异常规则：</strong>{{ item.exceptionRule }}</p>
      </div>
    </el-card>

    <el-card v-if="item" class="booking-card" data-testid="medical-booking-section">
      <h3>预约医疗服务</h3>
      <el-form :model="form" label-width="110px">
        <el-form-item label="医疗资源">
          <el-select v-model="form.resourceId" placeholder="选择诊室/兽医" @change="loadSlots">
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
        <el-form-item label="宠物 ID"><el-input v-model="form.petId" placeholder="可选，用于宠物归属与冲突校验" /></el-form-item>
        <el-form-item label="联系人"><el-input v-model="form.customerName" /></el-form-item>
        <el-form-item label="手机号"><el-input v-model="form.customerPhone" /></el-form-item>
        <el-form-item label="医疗备注"><el-input v-model="form.remark" type="textarea" :rows="2" placeholder="病情描述/既往病史/近期用药/注意事项" /></el-form-item>
      </el-form>
      <el-button type="primary" :loading="submitting" @click="submit">提交医疗预约</el-button>
    </el-card>
  </section>
</template>

<script>
import { getServiceItem, listServiceResources, listServiceSlots, createServiceBooking } from '@/api/service'

export default {
  name: 'MedicalDetailView',
  data() {
    return {
      item: null,
      resources: [],
      slots: [],
      form: { resourceId: undefined, slotId: undefined, petId: undefined, customerName: '', customerPhone: '', remark: '' },
      submitting: false
    }
  },
  computed: {
    profile() {
      return (this.item && this.item.medicalProfile) || {}
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
        if (response.data.kind !== 'MEDICAL') {
          this.$message && this.$message.error('该服务不是医疗项目')
          this.$router && this.$router.push({ name: 'medical' })
          return
        }
        this.item = response.data
      } catch (error) {
        this.$message && this.$message.error(error.message)
      }
    },
    async loadResources() {
      try {
        const response = await listServiceResources({ serviceItemId: this.$route.params.id, status: 'ACTIVE', page: 1, size: 100 })
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
        this.$message && this.$message.warning('请选择医疗资源、窗口并填写联系人与手机号')
        return
      }
      this.submitting = true
      try {
        const response = await createServiceBooking({
          serviceItemId: this.$route.params.id,
          resourceId: this.form.resourceId,
          slotId: this.form.slotId,
          petId: this.form.petId || undefined,
          customerName: this.form.customerName,
          customerPhone: this.form.customerPhone,
          remark: this.form.remark || undefined
        })
        this.$message && this.$message.success('医疗预约已创建：' + response.data.bookingNo)
        this.$router && this.$router.push({ name: 'my-medical-bookings' })
      } catch (error) {
        this.$message && this.$message.error(error.message)
      } finally {
        this.submitting = false
      }
    },
    formatSlot(slot) {
      const left = (slot.capacity || 0) - (slot.bookedCount || 0)
      return `${this.formatTime(slot.startAt)} ~ ${this.formatTime(slot.endAt)}（余 ${left}）`
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
.medical-detail-page { max-width: 900px; margin: 24px auto; }
.price { color: #f56c6c; font-size: 18px; font-weight: 700; }
.rules, .transparency { margin-top: 12px; padding: 12px; background: #f5f7fa; border-radius: 6px; }
.rules p, .transparency p { margin: 6px 0; }
.booking-card { margin-top: 16px; }
</style>
