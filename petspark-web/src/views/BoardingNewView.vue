<template>
  <section class="boarding-new">
    <h2 data-testid="boarding-new-title">发起寄养预约</h2>
    <el-form :model="form" :rules="rules" ref="form" label-width="100px">
      <el-form-item label="宠物" prop="petId">
        <el-select v-model="form.petId" placeholder="选择宠物" data-testid="boarding-pet-select">
          <el-option v-for="pet in pets" :key="pet.id" :label="pet.name" :value="pet.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="开始日期" prop="startDate">
        <el-date-picker v-model="form.startDate" type="date" value-format="yyyy-MM-dd" data-testid="boarding-start-date" />
      </el-form-item>
      <el-form-item label="结束日期" prop="endDate">
        <el-date-picker v-model="form.endDate" type="date" value-format="yyyy-MM-dd" data-testid="boarding-end-date" />
      </el-form-item>
      <el-form-item label="紧急联系人">
        <el-input v-model="care.emergencyContact" maxlength="32" data-testid="boarding-emergency-contact" />
      </el-form-item>
      <el-form-item label="喂养计划">
        <el-input v-model="care.feedingPlan" type="textarea" :rows="2" maxlength="500" />
      </el-form-item>
      <el-form-item label="用药计划">
        <el-input v-model="care.medicationPlan" type="textarea" :rows="2" maxlength="500" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="submitting" @click="submit" data-testid="boarding-submit">提交预约</el-button>
        <el-button @click="checkAvailability" data-testid="boarding-check-availability">查询可用房间</el-button>
      </el-form-item>
    </el-form>

    <el-dialog title="可用房间" :visible.sync="showAvailability" width="640px">
      <el-table :data="rooms" data-testid="boarding-availability-table">
        <el-table-column prop="roomName" label="房间" />
        <el-table-column prop="capacity" label="容量" width="80" />
        <el-table-column prop="availableCount" label="可用" width="80" />
        <el-table-column label="状态" width="80">
          <template slot-scope="{ row }">
            <el-tag :type="row.open ? 'success' : 'info'">{{ row.open ? '有位' : '已满' }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </section>
</template>

<script>
import { createBooking, queryAvailability } from '@/api/boarding'
import { listPets } from '@/api/pets'

export default {
  name: 'BoardingNewView',
  data() {
    return {
      pets: [],
      form: { petId: undefined, startDate: '', endDate: '' },
      care: { emergencyContact: '', feedingPlan: '', medicationPlan: '' },
      submitting: false,
      showAvailability: false,
      rooms: [],
      rules: {
        petId: [{ required: true, message: '请选择宠物', trigger: 'change' }],
        startDate: [{ required: true, message: '请选择开始日期', trigger: 'change' }],
        endDate: [{ required: true, message: '请选择结束日期', trigger: 'change' }]
      }
    }
  },
  created() {
    this.loadPets()
  },
  methods: {
    async loadPets() {
      try {
        const response = await listPets({ page: 1, size: 50 })
        this.pets = response.data.items || []
      } catch (error) {
        this.$message && this.$message.error(error.message)
      }
    },
    async checkAvailability() {
      if (!this.form.petId || !this.form.startDate || !this.form.endDate) {
        this.$message && this.$message.warning('请先选择宠物与日期')
        return
      }
      try {
        const response = await queryAvailability({
          petId: this.form.petId,
          startDate: this.form.startDate,
          endDate: this.form.endDate
        })
        this.rooms = response.data || []
        this.showAvailability = true
      } catch (error) {
        this.$message && this.$message.error(error.message)
      }
    },
    async submit() {
      this.$refs.form.validate(async (valid) => {
        if (!valid) return
        this.submitting = true
        try {
          const payload = {
            petId: this.form.petId,
            startDate: this.form.startDate,
            endDate: this.form.endDate,
            careProfile: {
              emergencyContact: this.care.emergencyContact,
              feedingPlan: this.care.feedingPlan,
              medicationPlan: this.care.medicationPlan
            }
          }
          const idempotencyKey = `boarding-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`
          await createBooking(payload, idempotencyKey)
          this.$message && this.$message.success('寄养预约已提交，等待确认')
          this.$router.push({ name: 'my-boarding' })
        } catch (error) {
          this.$message && this.$message.error(error.message)
        } finally {
          this.submitting = false
        }
      })
    }
  }
}
</script>

<style scoped>
.boarding-new { max-width: 640px; margin: 24px auto; }
</style>
