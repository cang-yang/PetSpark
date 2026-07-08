<template>
  <section class="pet-health-page">
    <el-card>
      <div class="toolbar">
        <h2 data-testid="pet-health-title">宠物健康记录</h2>
        <el-select v-model="filters.recordType" placeholder="类型筛选" clearable @change="loadRecords">
          <el-option label="疫苗" value="VACCINATION" />
          <el-option label="体检" value="CHECKUP" />
          <el-option label="用药" value="MEDICATION" />
          <el-option label="手术" value="SURGERY" />
          <el-option label="其他" value="OTHER" />
        </el-select>
        <el-button type="primary" @click="loadRecords">刷新</el-button>
        <el-button @click="openCreate">新增记录</el-button>
      </div>
      <div v-if="records.length" data-testid="health-list">
        <div v-for="item in records" :key="item.id" class="health-row" :data-testid="`health-${item.id}`">
          <div class="health-head">
            <el-tag :type="item.status === 'ACTIVE' ? 'success' : 'info'">{{ statusLabel(item.status) }}</el-tag>
            <span class="occurred">{{ item.occurredOn }}</span>
            <span class="type">{{ typeLabel(item.recordType) }}</span>
            <span class="summary">{{ item.summary }}</span>
          </div>
          <div v-if="item.status === 'ACTIVE' && item.detail" class="detail">详情：{{ item.detail }}</div>
          <div v-else-if="item.status === 'ERASED'" class="detail erased">该记录已完成隐私清除</div>
          <div v-if="item.authorName" class="author">作者：{{ item.authorName }}</div>
          <div class="actions">
            <el-button size="mini" @click="openRevise(item)">修订</el-button>
            <el-button size="mini" type="danger" @click="erase(item)">清除</el-button>
          </div>
        </div>
      </div>
      <p v-else-if="!loading" data-testid="health-empty">暂无健康记录</p>
      <el-pagination
        v-if="total > page.size"
        :current-page="page.page"
        :page-size="page.size"
        :total="total"
        layout="prev, pager, next"
        @current-change="changePage" />
    </el-card>

    <el-dialog title="新增健康记录" :visible.sync="showCreate">
      <el-form :model="form" label-width="100px">
        <el-form-item label="类型">
          <el-select v-model="form.recordType">
            <el-option label="疫苗" value="VACCINATION" />
            <el-option label="体检" value="CHECKUP" />
            <el-option label="用药" value="MEDICATION" />
            <el-option label="手术" value="SURGERY" />
            <el-option label="其他" value="OTHER" />
          </el-select>
        </el-form-item>
        <el-form-item label="发生日期"><el-date-picker v-model="form.occurredOn" type="date" value-format="yyyy-MM-dd" /></el-form-item>
        <el-form-item label="摘要"><el-input v-model="form.summary" maxlength="200" show-word-limit /></el-form-item>
        <el-form-item label="详情"><el-input v-model="form.detail" type="textarea" :rows="4" maxlength="4000" show-word-limit /></el-form-item>
        <el-form-item label="附件文件 ID"><el-input v-model="form.attachmentFileId" /></el-form-item>
      </el-form>
      <span slot="footer">
        <el-button @click="showCreate = false">取消</el-button>
        <el-button type="primary" @click="submitCreate">保存</el-button>
      </span>
    </el-dialog>

    <el-dialog title="修订健康记录" :visible.sync="showRevise">
      <el-form :model="reviseForm" label-width="100px">
        <el-form-item label="类型">
          <el-select v-model="reviseForm.recordType">
            <el-option label="疫苗" value="VACCINATION" />
            <el-option label="体检" value="CHECKUP" />
            <el-option label="用药" value="MEDICATION" />
            <el-option label="手术" value="SURGERY" />
            <el-option label="其他" value="OTHER" />
          </el-select>
        </el-form-item>
        <el-form-item label="发生日期"><el-date-picker v-model="reviseForm.occurredOn" type="date" value-format="yyyy-MM-dd" /></el-form-item>
        <el-form-item label="摘要"><el-input v-model="reviseForm.summary" maxlength="200" show-word-limit /></el-form-item>
        <el-form-item label="详情"><el-input v-model="reviseForm.detail" type="textarea" :rows="4" maxlength="4000" show-word-limit /></el-form-item>
        <el-form-item label="附件文件 ID"><el-input v-model="reviseForm.attachmentFileId" /></el-form-item>
      </el-form>
      <span slot="footer">
        <el-button @click="showRevise = false">取消</el-button>
        <el-button type="primary" @click="submitRevise">保存修订</el-button>
      </span>
    </el-dialog>
  </section>
</template>

<script>
import { listHealthRecords, createHealthRecord, reviseHealthRecord, eraseHealthRecord } from '@/api/health'

export default {
  name: 'PetHealthView',
  data() {
    return {
      loading: false,
      records: [],
      total: 0,
      page: { page: 1, size: 20 },
      filters: { recordType: undefined },
      showCreate: false,
      showRevise: false,
      form: this.emptyForm(),
      reviseForm: this.emptyForm(),
      reviseTargetId: null
    }
  },
  computed: {
    petId() {
      return this.$route.params.id
    }
  },
  created() {
    this.loadRecords()
  },
  methods: {
    emptyForm() {
      return { recordType: 'CHECKUP', occurredOn: '', summary: '', detail: '', attachmentFileId: '' }
    },
    async loadRecords() {
      this.loading = true
      try {
        const response = await listHealthRecords(this.petId, {
          page: this.page.page,
          size: this.page.size,
          recordType: this.filters.recordType || undefined
        })
        this.records = response.data.items || []
        this.total = response.data.total || 0
      } catch (error) {
        this.$message && this.$message.error(error.message)
      } finally {
        this.loading = false
      }
    },
    changePage(page) {
      this.page.page = page
      this.loadRecords()
    },
    openCreate() {
      this.form = this.emptyForm()
      this.showCreate = true
    },
    async submitCreate() {
      try {
        await createHealthRecord(this.petId, this.form)
        this.showCreate = false
        this.$message && this.$message.success('健康记录已创建')
        await this.loadRecords()
      } catch (error) {
        this.$message && this.$message.error(error.message)
      }
    },
    openRevise(row) {
      this.reviseTargetId = row.id
      this.reviseForm = {
        recordType: row.recordType,
        occurredOn: row.occurredOn,
        summary: row.summary,
        detail: row.detail || '',
        attachmentFileId: row.attachmentFileId || ''
      }
      this.showRevise = true
    },
    async submitRevise() {
      try {
        await reviseHealthRecord(this.reviseTargetId, this.reviseForm)
        this.showRevise = false
        this.$message && this.$message.success('修订已保存')
        await this.loadRecords()
      } catch (error) {
        this.$message && this.$message.error(error.message)
      }
    },
    async erase(row) {
      const reason = window.prompt('请输入隐私清除原因')
      if (!reason) return
      try {
        await eraseHealthRecord(row.id, { reason })
        this.$message && this.$message.success('已清除敏感内容')
        await this.loadRecords()
      } catch (error) {
        this.$message && this.$message.error(error.message)
      }
    },
    statusLabel(status) {
      return status === 'ACTIVE' ? '有效' : status === 'ERASED' ? '已清除' : status
    },
    typeLabel(type) {
      return ({
        VACCINATION: '疫苗',
        CHECKUP: '体检',
        MEDICATION: '用药',
        SURGERY: '手术',
        OTHER: '其他'
      })[type] || type
    }
  }
}
</script>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}
.toolbar h2 {
  margin: 0;
}
.health-row {
  padding: 12px 0;
  border-bottom: 1px solid #ebeef5;
}
.health-head {
  display: flex;
  gap: 12px;
  align-items: center;
}
.occurred {
  color: #909399;
  font-size: 13px;
}
.type {
  color: #409eff;
  font-weight: 600;
}
.summary {
  font-weight: 600;
}
.detail {
  margin-top: 8px;
  color: #606266;
}
.detail.erased {
  color: #c0c4cc;
  font-style: italic;
}
.author {
  margin-top: 4px;
  color: #909399;
  font-size: 12px;
}
.actions {
  margin-top: 8px;
}
</style>