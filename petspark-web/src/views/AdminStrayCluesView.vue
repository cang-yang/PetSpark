<template>
  <section class="admin-stray-clues">
    <h2 data-testid="admin-stray-title">流浪救助线索管理</h2>
    <div class="toolbar">
      <el-input v-model="filters.keyword" placeholder="线索号/位置/描述" clearable />
      <el-select v-model="filters.status" placeholder="状态" clearable>
        <el-option label="待受理" value="SUBMITTED" />
        <el-option label="已指派" value="ASSIGNED" />
        <el-option label="救助中" value="IN_RESCUE" />
        <el-option label="已解决" value="RESOLVED" />
        <el-option label="已关闭" value="CLOSED" />
      </el-select>
      <el-button type="primary" @click="loadClues">查询</el-button>
    </div>

    <el-table :data="clues" data-testid="admin-stray-table">
      <el-table-column prop="clueNo" label="线索号" width="170" />
      <el-table-column label="动物" width="80"><template slot-scope="{ row }">{{ animalLabel(row.animalType) }}</template></el-table-column>
      <el-table-column prop="location" label="位置" />
      <el-table-column label="状态" width="110"><template slot-scope="{ row }"><el-tag :type="statusTagType(row.status)">{{ row.statusLabel || statusLabel(row.status) }}</el-tag></template></el-table-column>
      <el-table-column prop="assignedUserId" label="负责人" width="180" />
      <el-table-column label="操作" width="340">
        <template slot-scope="{ row }">
          <el-button size="mini" @click="openDetail(row)">详情</el-button>
          <el-button size="mini" type="primary" :disabled="row.status !== 'SUBMITTED'" @click="openAssign(row)">指派</el-button>
          <el-button size="mini" type="warning" :disabled="row.status !== 'ASSIGNED'" @click="openTransition(row, 'IN_RESCUE')">开始救助</el-button>
          <el-button size="mini" type="success" :disabled="!canResolve(row)" @click="openTransition(row, 'RESOLVED')">解决</el-button>
          <el-button size="mini" type="info" :disabled="row.status === 'CLOSED'" @click="openTransition(row, 'CLOSED')">关闭</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog title="线索详情" :visible.sync="showDetail" width="680px" append-to-body>
      <div v-if="current" data-testid="admin-stray-detail">
        <p><strong>线索号：</strong>{{ current.clueNo }}</p>
        <p><strong>提交人：</strong>{{ current.reporterUserId }}</p>
        <p><strong>动物类型：</strong>{{ animalLabel(current.animalType) }}</p>
        <p><strong>发现位置：</strong>{{ current.location }}</p>
        <p><strong>现场描述：</strong>{{ current.description }}</p>
        <p><strong>联系电话：</strong>{{ current.contactPhone || '未填写' }}</p>
        <p><strong>状态：</strong>{{ current.statusLabel || statusLabel(current.status) }}</p>
        <p v-if="current.adminNote"><strong>处理备注：</strong>{{ current.adminNote }}</p>
        <p v-if="current.handoffPetId"><strong>宠物建档占位：</strong>{{ current.handoffPetId }}</p>
        <p v-if="current.handoffNote"><strong>后续说明：</strong>{{ current.handoffNote }}</p>
        <div v-if="current.images && current.images.length" class="thumbs">
          <img v-for="img in current.images" :key="img.fileId" :src="img.previewUrl" alt="线索图片">
        </div>
      </div>
      <span slot="footer"><el-button @click="showDetail = false">关闭</el-button></span>
    </el-dialog>

    <el-dialog title="指派救助负责人" :visible.sync="showAssign" width="520px" append-to-body>
      <el-form label-width="110px">
        <el-form-item label="负责人用户ID" required><el-input v-model="assignForm.assignedUserId" maxlength="36" /></el-form-item>
        <el-form-item label="处理备注"><el-input v-model="assignForm.note" type="textarea" :rows="3" maxlength="500" show-word-limit /></el-form-item>
      </el-form>
      <span slot="footer">
        <el-button @click="showAssign = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitAssign">确认指派</el-button>
      </span>
    </el-dialog>

    <el-dialog :title="transitionTitle" :visible.sync="showTransition" width="560px" append-to-body>
      <el-form label-width="110px">
        <el-form-item label="处理备注"><el-input v-model="transitionForm.note" type="textarea" :rows="3" maxlength="500" show-word-limit /></el-form-item>
        <el-form-item v-if="transitionForm.status === 'RESOLVED'" label="宠物ID占位"><el-input v-model="transitionForm.handoffPetId" maxlength="36" placeholder="选填：后续建档/领养占位引用" /></el-form-item>
        <el-form-item v-if="transitionForm.status === 'RESOLVED'" label="后续说明"><el-input v-model="transitionForm.handoffNote" type="textarea" :rows="3" maxlength="500" show-word-limit /></el-form-item>
      </el-form>
      <span slot="footer">
        <el-button @click="showTransition = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitTransition">确认更新</el-button>
      </span>
    </el-dialog>
  </section>
</template>

<script>
import { assignStrayClue, getAdminStrayClue, listAdminStrayClues, transitionStrayClue } from '@/api/stray'

export default {
  name: 'AdminStrayCluesView',
  data() {
    return {
      clues: [],
      total: 0,
      filters: { keyword: undefined, status: undefined },
      page: { page: 1, size: 20 },
      current: null,
      showDetail: false,
      showAssign: false,
      showTransition: false,
      saving: false,
      assignForm: { id: null, assignedUserId: '', note: '', version: 0 },
      transitionForm: { id: null, status: '', note: '', handoffPetId: '', handoffNote: '', version: 0 }
    }
  },
  computed: {
    transitionTitle() {
      return `更新状态：${this.statusLabel(this.transitionForm.status)}`
    }
  },
  created() {
    this.loadClues()
  },
  methods: {
    async loadClues() {
      try {
        const response = await listAdminStrayClues({
          keyword: this.filters.keyword || undefined,
          status: this.filters.status || undefined,
          page: this.page.page,
          size: this.page.size
        })
        this.clues = response.data.items || []
        this.total = response.data.total || 0
      } catch (error) {
        this.$message && this.$message.error(error.message)
      }
    },
    async openDetail(row) {
      try {
        const response = await getAdminStrayClue(row.id)
        this.current = response.data
        this.showDetail = true
      } catch (error) {
        this.$message && this.$message.error(error.message)
      }
    },
    openAssign(row) {
      this.assignForm = { id: row.id, assignedUserId: row.assignedUserId || '', note: row.adminNote || '', version: row.version }
      this.showAssign = true
    },
    async submitAssign() {
      if (!this.assignForm.assignedUserId || !this.assignForm.assignedUserId.trim()) {
        this.$message && this.$message.warning('请填写负责人用户ID')
        return
      }
      this.saving = true
      try {
        await assignStrayClue(this.assignForm.id, {
          assignedUserId: this.assignForm.assignedUserId.trim(),
          note: this.assignForm.note,
          version: this.assignForm.version
        })
        this.$message && this.$message.success('线索已指派')
        this.showAssign = false
        await this.loadClues()
      } catch (error) {
        this.$message && this.$message.error(error.message)
      } finally {
        this.saving = false
      }
    },
    openTransition(row, status) {
      this.transitionForm = {
        id: row.id,
        status,
        note: row.adminNote || '',
        handoffPetId: row.handoffPetId || '',
        handoffNote: row.handoffNote || '',
        version: row.version
      }
      this.showTransition = true
    },
    async submitTransition() {
      this.saving = true
      try {
        await transitionStrayClue(this.transitionForm.id, {
          status: this.transitionForm.status,
          note: this.transitionForm.note,
          handoffPetId: this.transitionForm.handoffPetId || undefined,
          handoffNote: this.transitionForm.handoffNote || undefined,
          version: this.transitionForm.version
        })
        this.$message && this.$message.success('状态已更新')
        this.showTransition = false
        await this.loadClues()
      } catch (error) {
        this.$message && this.$message.error(error.message)
      } finally {
        this.saving = false
      }
    },
    canResolve(row) {
      return row && (row.status === 'ASSIGNED' || row.status === 'IN_RESCUE')
    },
    animalLabel(type) {
      return { DOG: '狗', CAT: '猫', OTHER: '其他' }[type] || type
    },
    statusLabel(status) {
      return { SUBMITTED: '待受理', ASSIGNED: '已指派', IN_RESCUE: '救助中', RESOLVED: '已解决', CLOSED: '已关闭' }[status] || status
    },
    statusTagType(status) {
      if (status === 'RESOLVED') return 'success'
      if (status === 'CLOSED') return 'info'
      if (status === 'IN_RESCUE') return 'warning'
      return ''
    }
  }
}
</script>

<style scoped>
.admin-stray-clues { max-width: 1180px; margin: 24px auto; }
.toolbar { display: flex; gap: 12px; margin-bottom: 16px; }
.thumbs { display: flex; gap: 10px; flex-wrap: wrap; }
.thumbs img { width: 120px; height: 90px; object-fit: cover; border-radius: 6px; }
</style>
