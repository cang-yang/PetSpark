<template>
  <main class="pet-health-page">
    <page-header
      title="宠物健康记录"
      description="按时间回看疫苗、体检、用药与护理变化。"
      data-testid="pet-health-title"
    >
      <template #actions
        ><el-button type="primary" @click="openCreate"
          >新增记录</el-button
        ></template
      >
    </page-header>
    <filter-bar>
      <el-select
        v-model="filters.recordType"
        placeholder="类型筛选"
        clearable
        @change="loadRecords"
      >
        <el-option label="疫苗" value="VACCINATION" />
        <el-option label="体检" value="CHECKUP" />
        <el-option label="用药" value="MEDICATION" />
        <el-option label="手术" value="SURGERY" />
        <el-option label="其他" value="OTHER" />
      </el-select>
      <template #actions
        ><el-button @click="loadRecords">刷新记录</el-button></template
      >
    </filter-bar>

    <loading-state v-if="loading" text="正在读取健康档案…" />
    <error-state
      v-else-if="error"
      title="健康记录暂时没有加载出来"
      :description="error"
      @retry="loadRecords"
    />
    <empty-state
      v-else-if="!records.length"
      title="还没有健康记录"
      description="从一次体检、疫苗或用药记录开始，为变化留下依据。"
      :image="emptyPetImage"
      data-testid="health-empty"
    />
    <ol v-else class="health-timeline" data-testid="health-list">
      <li
        v-for="item in records"
        :key="item.id"
        class="health-row"
        :data-testid="`health-${item.id}`"
      >
        <div class="health-marker" aria-hidden="true" />
        <article class="health-card">
          <header class="health-head">
            <div>
              <time class="occurred">{{ item.occurredOn }}</time
              ><span class="type">{{ typeLabel(item.recordType) }}</span>
            </div>
            <el-tag :type="item.status === 'ACTIVE' ? 'success' : 'info'">{{
              statusLabel(item.status)
            }}</el-tag>
          </header>
          <h2 class="summary">{{ item.summary }}</h2>
          <div v-if="item.status === 'ACTIVE' && item.detail" class="detail">
            详情：{{ item.detail }}
          </div>
          <div v-else-if="item.status === 'ERASED'" class="detail erased">
            该记录已完成隐私清除
          </div>
          <div v-if="item.authorName" class="author">
            作者：{{ item.authorName }}
          </div>
          <div class="actions">
            <el-button size="mini" @click="openRevise(item)">修订</el-button>
            <el-button size="mini" type="danger" @click="erase(item)"
              >清除</el-button
            >
          </div>
        </article>
      </li>
    </ol>
    <el-pagination
      v-if="total > page.size"
      :current-page="page.page"
      :page-size="page.size"
      :total="total"
      layout="prev, pager, next"
      @current-change="changePage"
    />

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
        <el-form-item label="发生日期"
          ><el-date-picker
            v-model="form.occurredOn"
            type="date"
            value-format="yyyy-MM-dd"
        /></el-form-item>
        <el-form-item label="摘要"
          ><el-input v-model="form.summary" maxlength="200" show-word-limit
        /></el-form-item>
        <el-form-item label="详情"
          ><el-input
            v-model="form.detail"
            type="textarea"
            :rows="4"
            maxlength="4000"
            show-word-limit
        /></el-form-item>
        <el-form-item label="附件文件 ID"
          ><el-input v-model="form.attachmentFileId"
        /></el-form-item>
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
        <el-form-item label="发生日期"
          ><el-date-picker
            v-model="reviseForm.occurredOn"
            type="date"
            value-format="yyyy-MM-dd"
        /></el-form-item>
        <el-form-item label="摘要"
          ><el-input
            v-model="reviseForm.summary"
            maxlength="200"
            show-word-limit
        /></el-form-item>
        <el-form-item label="详情"
          ><el-input
            v-model="reviseForm.detail"
            type="textarea"
            :rows="4"
            maxlength="4000"
            show-word-limit
        /></el-form-item>
        <el-form-item label="附件文件 ID"
          ><el-input v-model="reviseForm.attachmentFileId"
        /></el-form-item>
      </el-form>
      <span slot="footer">
        <el-button @click="showRevise = false">取消</el-button>
        <el-button type="primary" @click="submitRevise">保存修订</el-button>
      </span>
    </el-dialog>
  </main>
</template>

<script>
import {
  listHealthRecords,
  createHealthRecord,
  reviseHealthRecord,
  eraseHealthRecord,
} from '@/api/health'
import PageHeader from '@/components/ui/PageHeader.vue'
import FilterBar from '@/components/ui/FilterBar.vue'
import LoadingState from '@/components/ui/LoadingState.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import emptyPetImage from '@/assets/illustrations/empty-pet.png'

export default {
  name: 'PetHealthView',
  components: { PageHeader, FilterBar, LoadingState, EmptyState, ErrorState },
  data() {
    return {
      loading: false,
      error: '',
      records: [],
      total: 0,
      page: { page: 1, size: 20 },
      filters: { recordType: undefined },
      showCreate: false,
      showRevise: false,
      form: this.emptyForm(),
      reviseForm: this.emptyForm(),
      reviseTargetId: null,
      emptyPetImage,
    }
  },
  computed: {
    petId() {
      return this.$route.params.id
    },
  },
  created() {
    this.loadRecords()
  },
  methods: {
    emptyForm() {
      return {
        recordType: 'CHECKUP',
        occurredOn: '',
        summary: '',
        detail: '',
        attachmentFileId: '',
      }
    },
    async loadRecords() {
      this.loading = true
      this.error = ''
      try {
        const response = await listHealthRecords(this.petId, {
          page: this.page.page,
          size: this.page.size,
          recordType: this.filters.recordType || undefined,
        })
        this.records = response.data.items || []
        this.total = response.data.total || 0
      } catch (error) {
        this.error =
          error.response?.data?.message ||
          error.message ||
          '请检查网络连接后重试。'
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
        attachmentFileId: row.attachmentFileId || '',
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
      return status === 'ACTIVE'
        ? '有效'
        : status === 'ERASED'
        ? '已清除'
        : status
    },
    typeLabel(type) {
      return (
        {
          VACCINATION: '疫苗',
          CHECKUP: '体检',
          MEDICATION: '用药',
          SURGERY: '手术',
          OTHER: '其他',
        }[type] || type
      )
    },
  },
}
</script>

<style scoped>
.pet-health-page {
  width: min(100%, 960px);
  margin: 0 auto;
  padding: 36px 24px 56px;
}
.ps-filter-bar .el-select {
  width: 220px;
}
.health-timeline {
  padding: 0;
  margin: 0;
  list-style: none;
}
.health-row {
  position: relative;
  display: grid;
  grid-template-columns: 28px minmax(0, 1fr);
  gap: 14px;
  padding-bottom: 18px;
}
.health-row:not(:last-child)::before {
  position: absolute;
  top: 20px;
  bottom: 0;
  left: 7px;
  width: 2px;
  content: '';
  background: var(--ps-color-border);
}
.health-marker {
  position: relative;
  z-index: 1;
  width: 16px;
  height: 16px;
  margin-top: 20px;
  background: var(--ps-color-green);
  border: 4px solid #e8f4eb;
  border-radius: 50%;
}
.health-card {
  padding: 18px 20px;
  background: var(--ps-color-surface);
  border: 1px solid var(--ps-color-border);
  border-radius: var(--ps-radius-lg);
  box-shadow: var(--ps-shadow-card);
}
.health-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.occurred {
  display: block;
  color: var(--ps-color-muted);
  font-size: 13px;
}
.type {
  color: var(--ps-color-green);
  font-weight: 600;
  font-size: 13px;
}
.occurred + .type::before {
  content: ' · ';
  color: var(--ps-color-border);
}
.summary {
  margin: 12px 0 0;
  color: var(--ps-color-text);
  font-size: 18px;
  font-weight: 600;
}
.detail {
  margin-top: 8px;
  color: var(--ps-color-muted);
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
  display: flex;
  justify-content: flex-end;
  margin-top: 8px;
}
@media (max-width: 640px) {
  .pet-health-page {
    padding: 24px 16px 40px;
  }
  .ps-filter-bar .el-select {
    width: 100%;
  }
  .health-row {
    grid-template-columns: 20px minmax(0, 1fr);
    gap: 8px;
  }
  .health-card {
    padding: 16px;
  }
}
</style>
