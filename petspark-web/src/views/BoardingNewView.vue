<template>
  <main class="boarding-page">
    <page-header
      title="发起寄养预约"
      description="提前说明日期与照护习惯，让寄养团队做好准备。"
      data-testid="boarding-new-title"
    />
    <loading-state v-if="loadingPets" text="正在读取宠物档案…" />
    <error-state
      v-else-if="petError"
      title="宠物档案暂时没有加载出来"
      :description="petError"
      @retry="loadPets"
    />
    <empty-state
      v-else-if="!pets.length"
      title="先添加一只需要照护的伙伴"
      description="寄养预约需要关联您的宠物档案。"
      action-text="去添加宠物"
      :image="emptyPetImage"
      @action="$router.push('/my/pets')"
    />
    <div v-else class="booking-layout">
      <el-form
        ref="form"
        class="booking-form"
        :model="form"
        :rules="rules"
        label-position="top"
      >
        <section class="form-section">
          <header>
            <span>1</span>
            <div>
              <h2>选择宠物</h2>
              <p>确认本次需要寄养的小伙伴。</p>
            </div>
          </header>
          <el-form-item label="宠物" prop="petId"
            ><el-select
              v-model="form.petId"
              placeholder="选择宠物"
              data-testid="boarding-pet-select"
              ><el-option
                v-for="pet in pets"
                :key="pet.id"
                :label="pet.name"
                :value="pet.id" /></el-select
          ></el-form-item>
        </section>
        <section class="form-section">
          <header>
            <span>2</span>
            <div>
              <h2>选择日期</h2>
              <p>先查询房间，再提交正式预约。</p>
            </div>
          </header>
          <div class="date-grid">
            <el-form-item label="开始日期" prop="startDate"
              ><el-date-picker
                v-model="form.startDate"
                type="date"
                value-format="yyyy-MM-dd"
                data-testid="boarding-start-date" /></el-form-item
            ><el-form-item label="结束日期" prop="endDate"
              ><el-date-picker
                v-model="form.endDate"
                type="date"
                value-format="yyyy-MM-dd"
                data-testid="boarding-end-date"
            /></el-form-item>
          </div>
          <el-button
            plain
            data-testid="boarding-check-availability"
            @click="checkAvailability"
            >查询可用房间</el-button
          >
        </section>
        <section class="form-section">
          <header>
            <span>3</span>
            <div>
              <h2>照护信息</h2>
              <p>仅填写寄养期间真正需要的内容。</p>
            </div>
          </header>
          <el-form-item label="紧急联系人"
            ><el-input
              v-model="care.emergencyContact"
              maxlength="32"
              data-testid="boarding-emergency-contact" /></el-form-item
          ><el-form-item label="喂养计划"
            ><el-input
              v-model="care.feedingPlan"
              type="textarea"
              :rows="2"
              maxlength="500" /></el-form-item
          ><el-form-item label="用药计划"
            ><el-input
              v-model="care.medicationPlan"
              type="textarea"
              :rows="2"
              maxlength="500"
          /></el-form-item>
        </section>
        <section class="form-section form-section--confirm">
          <header>
            <span>4</span>
            <div>
              <h2>确认预约</h2>
              <p>提交后等待寄养团队确认房间与照护安排。</p>
            </div>
          </header>
          <el-button
            type="primary"
            :loading="submitting"
            data-testid="boarding-submit"
            @click="submit"
            >提交预约</el-button
          >
        </section>
      </el-form>
      <aside class="booking-summary">
        <p class="booking-summary__eyebrow">预约摘要</p>
        <h2>{{ selectedPet ? selectedPet.name : '等待选择宠物' }}</h2>
        <dl>
          <div>
            <dt>入住</dt>
            <dd>{{ form.startDate || '待选择' }}</dd>
          </div>
          <div>
            <dt>离店</dt>
            <dd>{{ form.endDate || '待选择' }}</dd>
          </div>
          <div>
            <dt>照护说明</dt>
            <dd>
              {{ care.feedingPlan || care.medicationPlan || '可在表单中补充' }}
            </dd>
          </div>
        </dl>
        <p class="booking-summary__notice">
          提交不代表预约已确认，请以“我的寄养”状态为准。
        </p>
      </aside>
    </div>

    <el-dialog
      title="可用房间"
      :visible.sync="showAvailability"
      width="min(640px, 92vw)"
      append-to-body
    >
      <div class="room-grid" data-testid="boarding-availability-table">
        <article
          v-for="room in rooms"
          :key="room.id || room.roomName"
          class="room-card"
        >
          <div>
            <h3>{{ room.roomName }}</h3>
            <p>容量 {{ room.capacity }} · 当前可用 {{ room.availableCount }}</p>
          </div>
          <el-tag :type="room.open ? 'success' : 'info'">{{
            room.open ? '有位' : '已满'
          }}</el-tag>
        </article>
        <p v-if="!rooms.length">当前日期没有可用房间，请调整日期后重试。</p>
      </div>
    </el-dialog>
  </main>
</template>

<script>
import { createBooking, queryAvailability } from '@/api/boarding'
import { listPets } from '@/api/pets'
import PageHeader from '@/components/ui/PageHeader.vue'
import LoadingState from '@/components/ui/LoadingState.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import emptyPetImage from '@/assets/illustrations/empty-pet.png'

export default {
  name: 'BoardingNewView',
  components: { PageHeader, LoadingState, EmptyState, ErrorState },
  data() {
    return {
      pets: [],
      loadingPets: false,
      petError: '',
      form: { petId: undefined, startDate: '', endDate: '' },
      care: { emergencyContact: '', feedingPlan: '', medicationPlan: '' },
      submitting: false,
      showAvailability: false,
      rooms: [],
      emptyPetImage,
      rules: {
        petId: [{ required: true, message: '请选择宠物', trigger: 'change' }],
        startDate: [
          { required: true, message: '请选择开始日期', trigger: 'change' },
        ],
        endDate: [
          { required: true, message: '请选择结束日期', trigger: 'change' },
        ],
      },
    }
  },
  computed: {
    selectedPet() {
      return this.pets.find((pet) => pet.id === this.form.petId) || null
    },
  },
  created() {
    this.loadPets()
  },
  methods: {
    async loadPets() {
      this.loadingPets = true
      this.petError = ''
      try {
        const response = await listPets({ page: 1, size: 50 })
        this.pets = response.data.data?.items || response.data.items || []
      } catch (error) {
        this.petError =
          error.response?.data?.message ||
          error.message ||
          '请检查网络连接后重试。'
      } finally {
        this.loadingPets = false
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
          endDate: this.form.endDate,
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
              medicationPlan: this.care.medicationPlan,
            },
          }
          const idempotencyKey = `boarding-${Date.now()}-${Math.random()
            .toString(36)
            .slice(2, 10)}`
          await createBooking(payload, idempotencyKey)
          this.$message && this.$message.success('寄养预约已提交，等待确认')
          this.$router.push({ name: 'my-boarding' })
        } catch (error) {
          this.$message && this.$message.error(error.message)
        } finally {
          this.submitting = false
        }
      })
    },
  },
}
</script>

<style scoped>
.boarding-page {
  width: min(100%, 1040px);
  margin: 0 auto;
  padding: 36px 24px 56px;
}
.booking-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  gap: 24px;
  align-items: start;
}
.booking-form {
  display: grid;
  gap: 16px;
}
.form-section {
  padding: 22px;
  background: var(--ps-color-surface);
  border: 1px solid var(--ps-color-border);
  border-radius: var(--ps-radius-lg);
  box-shadow: var(--ps-shadow-card);
}
.form-section header {
  display: flex;
  gap: 12px;
  margin-bottom: 18px;
}
.form-section header > span {
  display: grid;
  flex: 0 0 auto;
  width: 30px;
  height: 30px;
  place-items: center;
  color: #fff;
  background: var(--ps-color-peach);
  border-radius: 50%;
  font-weight: 800;
}
.form-section h2 {
  margin: 0;
  font-size: 18px;
}
.form-section header p {
  margin: 2px 0 0;
  color: var(--ps-color-muted);
  font-size: 13px;
}
.date-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}
.form-section :deep(.el-select),
.form-section :deep(.el-date-editor) {
  width: 100%;
}
.form-section--confirm {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
}
.form-section--confirm header {
  margin: 0;
}
.booking-summary {
  position: sticky;
  top: 92px;
  padding: 24px;
  background: linear-gradient(145deg, var(--ps-color-cream), #fff);
  border: 1px solid #f1ded2;
  border-radius: var(--ps-radius-lg);
  box-shadow: var(--ps-shadow-card);
}
.booking-summary__eyebrow {
  margin: 0;
  color: var(--ps-color-peach);
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.08em;
}
.booking-summary h2 {
  margin: 5px 0 18px;
}
.booking-summary dl {
  display: grid;
  gap: 12px;
  margin: 0;
}
.booking-summary dl div {
  padding-bottom: 10px;
  border-bottom: 1px solid rgba(36, 49, 61, 0.08);
}
.booking-summary dt {
  color: var(--ps-color-muted);
  font-size: 12px;
}
.booking-summary dd {
  margin: 2px 0 0;
  font-weight: 600;
  overflow-wrap: anywhere;
}
.booking-summary__notice {
  margin: 18px 0 0;
  color: var(--ps-color-warning);
  font-size: 12px;
}
.room-grid {
  display: grid;
  gap: 10px;
}
.room-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px;
  background: var(--ps-color-surface-soft);
  border-radius: var(--ps-radius-md);
}
.room-card h3,
.room-card p {
  margin: 0;
}
.room-card p {
  color: var(--ps-color-muted);
  font-size: 13px;
}
@media (max-width: 820px) {
  .booking-layout {
    grid-template-columns: 1fr;
  }
  .booking-summary {
    position: static;
    grid-row: 1;
  }
}
@media (max-width: 560px) {
  .boarding-page {
    padding: 24px 16px 40px;
  }
  .date-grid {
    grid-template-columns: 1fr;
  }
  .form-section--confirm {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
