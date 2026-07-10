<template>
  <section class="admin-console-page system-admin-view">
    <AdminPageHeader eyebrow="系统治理" title="系统配置与审计" description="维护非敏感运行配置、业务字典并追踪关键操作。" />
    <div class="system-admin-view__grid">
        <AdminTableShell title="非敏感配置" :total="configs.length">
          <el-table :data="configs" data-testid="config-table" empty-text="暂无配置">
            <el-table-column prop="configKey" label="配置键" min-width="180" />
            <el-table-column label="配置值" min-width="220">
              <template #default="{ row }">
                <el-input v-model.trim="row.configValue" size="mini" />
              </template>
            </el-table-column>
            <el-table-column prop="valueType" label="类型" width="100" />
            <el-table-column label="操作" width="90">
              <template #default="{ row }">
                <el-button size="mini" type="primary" @click="saveConfig(row)">保存</el-button>
              </template>
            </el-table-column>
          </el-table>
        </AdminTableShell>
        <AdminTableShell title="业务字典" :total="dictItems.length">
          <template #filters>
            <el-select v-model="selectedType" placeholder="选择字典" @change="loadItems">
              <el-option v-for="type in dictTypes" :key="type.code" :label="type.name" :value="type.code" />
            </el-select>
            <el-button size="mini" @click="loadDictionaries">刷新</el-button>
          </template>
          <el-table :data="dictItems" data-testid="dict-item-table" empty-text="暂无字典项">
            <el-table-column prop="itemKey" label="键" width="120" />
            <el-table-column prop="itemLabel" label="名称" />
            <el-table-column label="状态" width="110"><template #default="{ row }"><StatusTag :status="row.status || 'ACTIVE'" :label="row.status === 'INACTIVE' ? '停用' : '启用'" /></template></el-table-column>
          </el-table>
        </AdminTableShell>
    </div>

    <AdminTableShell title="审计日志" :total="auditTotal">
      <template #filters>
        <el-input v-model.trim="auditFilter.module" placeholder="模块" />
        <el-select v-model="auditFilter.result" clearable placeholder="结果">
          <el-option label="成功" value="SUCCESS" />
          <el-option label="失败" value="FAILURE" />
        </el-select>
        <el-button type="primary" :loading="auditLoading" @click="loadAudits">查询</el-button>
      </template>
      <el-table :data="audits" data-testid="audit-table" empty-text="暂无审计日志">
        <el-table-column prop="module" label="模块" width="110" />
        <el-table-column prop="action" label="动作" width="120" />
        <el-table-column prop="actorRole" label="角色" width="120" />
        <el-table-column label="结果" width="110"><template #default="{ row }"><StatusTag :status="row.result" :label="row.result === 'SUCCESS' ? '成功' : '失败'" /></template></el-table-column>
        <el-table-column prop="requestId" label="请求ID" min-width="220" />
        <el-table-column prop="createdAt" label="时间" min-width="160" />
      </el-table>
      <template #pagination><el-pagination background layout="prev, pager, next" :current-page="auditPage" :page-size="20" :total="auditTotal" @current-change="changeAuditPage" /></template>
    </AdminTableShell>
  </section>
</template>

<script>
import {
  listAuditLogs,
  listDictItems,
  listDictTypes,
  listSystemConfigs,
  updateSystemConfig
} from '@/api/system'
import AdminPageHeader from '@/components/ui/AdminPageHeader.vue'
import AdminTableShell from '@/components/ui/AdminTableShell.vue'
import StatusTag from '@/components/ui/StatusTag.vue'

export default {
  name: 'SystemAdminView',
  components: { AdminPageHeader, AdminTableShell, StatusTag },
  data() {
    return {
      configs: [],
      dictTypes: [],
      dictItems: [],
      selectedType: '',
      audits: [],
      auditTotal: 0,
      auditPage: 1,
      auditLoading: false,
      auditFilter: {
        module: '',
        result: ''
      }
    }
  },
  created() {
    this.loadAll()
  },
  methods: {
    async loadAll() {
      await Promise.all([this.loadConfigs(), this.loadDictionaries(), this.loadAudits()])
    },
    async loadConfigs() {
      const response = await listSystemConfigs()
      this.configs = (response.data || []).map(item => ({ ...item }))
    },
    async saveConfig(row) {
      try {
        const response = await updateSystemConfig(row.configKey, {
          configValue: row.configValue,
          valueType: row.valueType,
          description: row.description || '',
          version: row.version
        })
        Object.assign(row, response.data)
        this.$message.success('配置已保存')
      } catch (err) {
        this.$message.error(err.message)
      }
    },
    async loadDictionaries() {
      const response = await listDictTypes()
      this.dictTypes = response.data || []
      if (!this.selectedType && this.dictTypes.length) {
        this.selectedType = this.dictTypes[0].code
      }
      if (this.selectedType) {
        await this.loadItems()
      }
    },
    async loadItems() {
      const response = await listDictItems(this.selectedType)
      this.dictItems = response.data || []
    },
    async loadAudits() {
      this.auditLoading = true
      try {
        const response = await listAuditLogs({
          module: this.auditFilter.module || undefined,
          result: this.auditFilter.result || undefined,
          page: this.auditPage,
          size: 20
        })
        this.audits = response.data.items || []
        this.auditTotal = response.data.total || this.audits.length
      } catch (err) {
        this.$message.error(err.message)
      } finally {
        this.auditLoading = false
      }
    },
    changeAuditPage(page) {
      this.auditPage = page
      this.loadAudits()
    }
  }
}
</script>

<style scoped>
.admin-console-page { display: grid; gap: 20px; }
.system-admin-view__grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 20px; }
@media (max-width: 1100px) { .system-admin-view__grid { grid-template-columns: 1fr; } }
</style>
