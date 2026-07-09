<template>
  <section class="training-detail-page">
    <el-card v-if="item">
      <h2>{{ item.name }}</h2>
      <p class="kind">训练服务</p>
      <p v-if="item.description">{{ item.description }}</p>
      <p class="price">基础价：￥{{ item.basePrice }}</p>
      <div v-if="item.qualification" class="transparency">
        <h4>训练资质</h4>
        <p>{{ item.qualification }}</p>
      </div>
      <div v-if="item.availabilityNote" class="transparency">
        <h4>可预约时段</h4>
        <p>{{ item.availabilityNote }}</p>
      </div>
      <div v-if="item.exceptionRule" class="transparency">
        <h4>异常规则</h4>
        <p>{{ item.exceptionRule }}</p>
      </div>
    </el-card>

    <el-card v-if="item" class="booking-card" data-testid="training-application-section">
      <h3>提交训练申请</h3>
      <el-alert
        v-if="item.status !== 'ACTIVE'"
        title="该训练项目已停用，暂不可申请"
        type="warning"
        :closable="false" />
      <el-form :model="form" label-width="110px">
        <el-form-item label="训练资源">
          <el-select v-model="form.resourceId" placeholder="选择训练师/场地" @change="loadSlots">
            <el-option v-for="res in resources" :key="res.id" :label="res.name" :value="res.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="可用窗口">
          <el-select v-model="form.slotId" placeholder="选择训练时间">
            <el-option
              v-for="slot in slots"
              :key="slot.id"
              :label="formatSlot(slot)"
              :value="slot.id"
              :disabled="slot.status !== 'OPEN' || slot.bookedCount >= slot.capacity" />
          </el-select>
        </el-form-item>
        <el-form-item label="训练规格" v-if="activeSpecifications.length">
          <el-select v-model="form.specificationId" placeholder="可选训练规格" clearable>
            <el-option
              v-for="spec in activeSpecifications"
              :key="spec.id"
              :label="`${spec.name} +￥${spec.priceDelta}`"
              :value="spec.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="宠物"><el-input v-model="form.petId" placeholder="宠物 ID（可选）" /></el-form-item>
        <el-form-item label="联系人"><el-input v-model="form.customerName" /></el-form-item>
        <el-form-item label="手机号"><el-input v-model="form.customerPhone" /></el-form-item>
        <el-form-item label="训练目标"><el-input v-model="form.trainingGoal" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="行为问题"><el-input v-model="form.behaviorProblem" type="textarea" :rows="2" placeholder="如扑人、护食、焦虑等（可选）" /></el-form-item>
        <el-form-item label="训练强度">
          <el-select v-model="form.intensity" placeholder="训练强度">
            <el-option label="低强度" value="LOW" />
            <el-option label="中等强度" value="MEDIUM" />
            <el-option label="高强度" value="HIGH" />
          </el-select>
        </el-form-item>
        <el-form-item label="注意事项"><el-input v-model="form.attentionNote" type="textarea" :rows="2" placeholder="健康、应激、攻击风险等（可选）" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <div class="booking-actions">
        <el-button type="primary" :disabled="item.status !== 'ACTIVE'" :loading="submitting" @click="submit">提交训练申请</el-button>
      </div>
    </el-card>
  </section>
</template>

<script>
import { getTrainingItem, listTrainingResources, listTrainingSlots, createTrainingBooking } from '@/api/training'

export default {
  name: 'TrainingServiceDetailView',
  data() {
    return {
      item: null,
      resources: [],
      slots: [],
      form: {
        resourceId: undefined,
        slotId: undefined,
        specificationId: undefined,
        petId: undefined,
        customerName: '',
        customerPhone: '',
        trainingGoal: '',
        behaviorProblem: '',
        intensity: 'MEDIUM',
        attentionNote: '',
        remark: ''
      },
      submitting: false
    }
  },
  computed: {
    activeSpecifications() {
      return (this.item && this.item.specifications ? this.item.specifications : []).filter((s) => s.status === 'ACTIVE')
    }
  },
  created() {
    this.loadItem()
    this.loadResources()
  },
  methods: {
    async loadItem() {
      try {
        const response = await getTrainingItem(this.$route.params.id)
        this.item = response.data
      } catch (error) {
        this.$message && this.$message.error(error.message)
      }
    },
    async loadResources() {
      try {
        const response = await listTrainingResources({ serviceItemId: this.$route.params.id, status: 'ACTIVE', page: 1, size: 100 })
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
        const response = await listTrainingSlots({ resourceId: this.form.resourceId, page: 1, size: 100 })
        this.slots = response.data.items || []
      } catch (error) {
        this.$message && this.$message.error(error.message)
      }
    },
    async submit() {
      if (!this.form.resourceId || !this.form.slotId || !this.form.customerName || !this.form.customerPhone || !this.form.trainingGoal) {
        this.$message && this.$message.warning('请选择资源、窗口并填写联系人、手机号和训练目标')
        return
      }
      this.submitting = true
      try {
        const payload = {
          serviceItemId: this.$route.params.id,
          resourceId: this.form.resourceId,
          slotId: this.form.slotId,
          specificationId: this.form.specificationId || undefined,
          petId: this.form.petId || undefined,
          customerName: this.form.customerName,
          customerPhone: this.form.customerPhone,
          remark: this.form.remark || undefined,
          trainingGoal: this.form.trainingGoal,
          behaviorProblem: this.form.behaviorProblem || undefined,
          intensity: this.form.intensity,
          attentionNote: this.form.attentionNote || undefined
        }
        const response = await createTrainingBooking(payload)
        this.$message && this.$message.success('训练申请已提交：' + response.data.bookingNo)
        this.$router && this.$router.push('/my/training/bookings')
      } catch (error) {
        this.$message && this.$message.error(error.message)
      } finally {
        this.submitting = false
      }
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
.training-detail-page { max-width: 900px; margin: 24px auto; }
.kind { color: #909399; }
.price { color: #f56c6c; font-size: 18px; font-weight: 700; }
.transparency { margin-top: 12px; padding: 12px; background: #f5f7fa; border-radius: 6px; }
.transparency h4 { margin: 0 0 6px 0; color: #303133; }
.booking-card { margin-top: 16px; }
.booking-actions { display: flex; gap: 12px; margin-top: 12px; }
</style>
