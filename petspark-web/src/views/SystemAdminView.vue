<template>
  <section class="system-admin-view">
    <h2>系统配置与审计</h2>
    <el-row :gutter="16">
      <el-col :span="12">
        <el-card>
          <h3>非敏感配置</h3>
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
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card>
          <h3>字典</h3>
          <div class="toolbar">
            <el-select v-model="selectedType" placeholder="选择字典" @change="loadItems">
              <el-option v-for="type in dictTypes" :key="type.code" :label="type.name" :value="type.code" />
            </el-select>
            <el-button size="mini" @click="loadDictionaries">刷新</el-button>
          </div>
          <el-table :data="dictItems" data-testid="dict-item-table" empty-text="暂无字典项">
            <el-table-column prop="itemKey" label="键" width="120" />
            <el-table-column prop="itemLabel" label="名称" />
            <el-table-column prop="status" label="状态" width="100" />
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <el-card class="audit-card">
      <h3>审计日志</h3>
      <div class="toolbar">
        <el-input v-model.trim="auditFilter.module" placeholder="模块" />
        <el-select v-model="auditFilter.result" clearable placeholder="结果">
          <el-option label="成功" value="SUCCESS" />
          <el-option label="失败" value="FAILURE" />
        </el-select>
        <el-button type="primary" :loading="auditLoading" @click="loadAudits">查询</el-button>
      </div>
      <el-table :data="audits" data-testid="audit-table" empty-text="暂无审计日志">
        <el-table-column prop="module" label="模块" width="110" />
        <el-table-column prop="action" label="动作" width="120" />
        <el-table-column prop="actorRole" label="角色" width="120" />
        <el-table-column prop="result" label="结果" width="100" />
        <el-table-column prop="requestId" label="请求ID" min-width="220" />
        <el-table-column prop="createdAt" label="时间" min-width="160" />
      </el-table>
    </el-card>
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

export default {
  name: 'SystemAdminView',
  data() {
    return {
      configs: [],
      dictTypes: [],
      dictItems: [],
      selectedType: '',
      audits: [],
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
          page: 1,
          size: 20
        })
        this.audits = response.data.items || []
      } catch (err) {
        this.$message.error(err.message)
      } finally {
        this.auditLoading = false
      }
    }
  }
}
</script>

<style scoped>
.system-admin-view {
  margin: 24px;
}

.toolbar {
  display: flex;
  gap: 10px;
  margin-bottom: 12px;
}

.audit-card {
  margin-top: 16px;
}
</style>
