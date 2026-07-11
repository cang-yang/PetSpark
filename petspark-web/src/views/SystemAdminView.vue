<template>
  <section class="admin-console-page system-admin-view">
    <AdminPageHeader
      eyebrow="平台治理"
      title="系统管理"
      description="用业务语言维护运营设置、查看平台选项，并追踪关键操作记录。"
    />

    <el-tabs v-model="activeTab" class="governance-tabs">
      <el-tab-pane label="运营设置" name="settings">
        <div class="settings-intro">
          <strong>这些设置会影响用户端行为</strong>
          <span>每项设置独立保存；技术配置键仅用于排查问题。</span>
        </div>
        <div class="settings-grid" data-testid="config-table">
          <article v-for="config in configs" :key="config.configKey" class="setting-card">
            <div class="setting-card__copy">
              <h2>{{ configMeta(config).label }}</h2>
              <p>{{ configMeta(config).description }}</p>
              <details class="technical-details"><summary>技术信息</summary><code>{{ config.configKey }}</code></details>
            </div>
            <div class="setting-card__control">
              <el-switch
                v-if="config.valueType === 'BOOLEAN'"
                :value="booleanConfigValue(config)"
                active-text="已开启"
                inactive-text="已关闭"
                @input="setBooleanConfigValue(config, $event)"
              />
              <el-input
                v-else-if="config.configKey === 'site.notice'"
                v-model="config.configValue"
                type="textarea"
                :rows="3"
                maxlength="200"
                show-word-limit
                placeholder="输入要向用户展示的公告，留空则不展示"
              />
              <el-input v-else v-model.trim="config.configValue" />
              <el-button type="primary" :loading="savingConfigKey === config.configKey" @click="saveConfig(config)">保存设置</el-button>
            </div>
          </article>
          <div v-if="!configs.length" class="empty-state">暂无可维护的运营设置</div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="业务选项" name="dictionaries">
        <AdminTableShell title="平台业务选项" :total="dictItems.length">
          <template #filters>
            <el-select v-model="selectedType" placeholder="选择选项类别" @change="loadItems">
              <el-option
                v-for="type in dictTypes"
                :key="type.code"
                :label="dictTypeLabel(type)"
                :value="type.code"
              />
            </el-select>
            <el-button :loading="dictLoading" @click="loadDictionaries">刷新</el-button>
          </template>
          <div class="dictionary-context">
            <div>
              <strong>{{ selectedDictType ? dictTypeLabel(selectedDictType) : '业务选项' }}</strong>
              <span>这些选项会出现在用户填写资料或业务表单时。</span>
            </div>
            <details v-if="selectedType" class="technical-details"><summary>技术信息</summary><code>{{ selectedType }}</code></details>
          </div>
          <el-table :data="dictItems" data-testid="dict-item-table" empty-text="暂无业务选项">
            <el-table-column label="显示名称" min-width="180">
              <template #default="{ row }">
                <div class="label-with-code">
                  <strong>{{ dictItemLabel(row) }}</strong>
                  <details class="technical-details"><summary>技术信息</summary><code>{{ row.itemKey }}</code></details>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="用途说明" min-width="260">
              <template #default="{ row }">{{ dictItemDescription(row) }}</template>
            </el-table-column>
            <el-table-column label="可用状态" width="120">
              <template #default="{ row }">
                <StatusTag :status="row.status || 'ACTIVE'" :label="row.status === 'INACTIVE' ? '已停用' : '可使用'" />
              </template>
            </el-table-column>
          </el-table>
        </AdminTableShell>
      </el-tab-pane>

      <el-tab-pane label="操作记录" name="audits">
        <AdminTableShell title="关键操作记录" :total="auditTotal">
          <template #filters>
            <el-input v-model.trim="auditFilter.module" clearable placeholder="按业务模块筛选" />
            <el-select v-model="auditFilter.result" clearable placeholder="全部结果">
              <el-option label="成功" value="SUCCESS" />
              <el-option label="失败" value="FAILURE" />
            </el-select>
            <el-button type="primary" :loading="auditLoading" @click="searchAudits">查询</el-button>
          </template>
          <div class="audit-guide">
            <strong>这里记录谁在什么时间做了什么</strong>
            <span>请求编号用于和服务端日志关联，不代表用户隐私信息。</span>
          </div>
          <el-table :data="audits" data-testid="audit-table" empty-text="暂无操作记录">
            <el-table-column label="业务模块" min-width="150">
              <template #default="{ row }">
                <div class="label-with-code"><strong>{{ auditModuleLabel(row.module) }}</strong></div>
              </template>
            </el-table-column>
            <el-table-column label="执行动作" min-width="140">
              <template #default="{ row }">
                <div class="label-with-code"><strong>{{ auditActionLabel(row.action) }}</strong></div>
              </template>
            </el-table-column>
            <el-table-column label="操作角色" min-width="150">
              <template #default="{ row }">
                <div class="label-with-code"><strong>{{ roleLabel(row.actorRole) }}</strong></div>
              </template>
            </el-table-column>
            <el-table-column label="结果" width="100">
              <template #default="{ row }">
                <StatusTag :status="row.result" :label="row.result === 'SUCCESS' ? '成功' : '失败'" />
              </template>
            </el-table-column>
            <el-table-column label="技术追踪" width="130">
              <template #default="{ row }">
                <details class="technical-details audit-technical-details">
                  <summary>查看详情</summary>
                  <code>模块：{{ row.module }}</code>
                  <code>动作：{{ row.action }}</code>
                  <code>角色：{{ row.actorRole }}</code>
                  <code>请求：{{ row.requestId }}</code>
                </details>
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" label="发生时间" min-width="170" />
          </el-table>
          <template #pagination>
            <el-pagination
              background
              layout="prev, pager, next"
              :current-page="auditPage"
              :page-size="20"
              :total="auditTotal"
              @current-change="changeAuditPage"
            />
          </template>
        </AdminTableShell>
      </el-tab-pane>
    </el-tabs>
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
import {
  getAuditActionLabel,
  getAuditModuleLabel,
  getConfigMeta,
  getDictItemLabel,
  getDictTypeLabel,
  getRoleMeta
} from '@/utils/adminTerminology'

export default {
  name: 'SystemAdminView',
  components: { AdminPageHeader, AdminTableShell, StatusTag },
  data() {
    return {
      activeTab: 'settings',
      configs: [],
      savingConfigKey: '',
      dictTypes: [],
      dictItems: [],
      selectedType: '',
      dictLoading: false,
      audits: [],
      auditTotal: 0,
      auditPage: 1,
      auditLoading: false,
      auditFilter: { module: '', result: '' }
    }
  },
  computed: {
    selectedDictType() { return this.dictTypes.find(type => type.code === this.selectedType) || null }
  },
  created() { this.loadAll() },
  methods: {
    async loadAll() { await Promise.all([this.loadConfigs(), this.loadDictionaries(), this.loadAudits()]) },
    async loadConfigs() {
      try {
        const response = await listSystemConfigs()
        this.configs = (response.data || []).map(item => ({ ...item, _savedConfigValue: item.configValue }))
      } catch (err) { this.$message.error(err.message) }
    },
    async saveConfig(row) {
      this.savingConfigKey = row.configKey
      try {
        const response = await updateSystemConfig(row.configKey, {
          configValue: row.configValue,
          valueType: row.valueType,
          description: row.description || '',
          version: row.version
        })
        Object.assign(row, response.data)
        row._savedConfigValue = row.configValue
        this.$message.success(`${this.configMeta(row).label}已保存`)
      } catch (err) {
        row.configValue = row._savedConfigValue
        this.$message.error(err.message)
      } finally { this.savingConfigKey = '' }
    },
    booleanConfigValue(row) { return String(row.configValue).toLowerCase() === 'true' },
    setBooleanConfigValue(row, value) { row.configValue = value ? 'true' : 'false' },
    configMeta(row) { return getConfigMeta(row) },
    async loadDictionaries() {
      this.dictLoading = true
      try {
        const response = await listDictTypes()
        this.dictTypes = response.data || []
        if (!this.selectedType && this.dictTypes.length) this.selectedType = this.dictTypes[0].code
        if (this.selectedType) await this.loadItems()
      } catch (err) { this.$message.error(err.message) } finally { this.dictLoading = false }
    },
    async loadItems() {
      try {
        const response = await listDictItems(this.selectedType)
        this.dictItems = response.data || []
      } catch (err) { this.$message.error(err.message) }
    },
    dictTypeLabel(type) { return getDictTypeLabel(type) },
    dictItemLabel(item) { return getDictItemLabel(item) },
    dictItemDescription(item) {
      const descriptions = {
        MALE: '宠物资料中用于表示雄性',
        FEMALE: '宠物资料中用于表示雌性',
        UNKNOWN: '性别尚未确认或用户选择不填写'
      }
      return descriptions[item.itemKey] || '平台业务表单中的可选值'
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
        this.auditTotal = response.data.total == null ? this.audits.length : response.data.total
      } catch (err) { this.$message.error(err.message) } finally { this.auditLoading = false }
    },
    searchAudits() { this.auditPage = 1; this.loadAudits() },
    changeAuditPage(page) { this.auditPage = page; this.loadAudits() },
    auditModuleLabel(module) { return getAuditModuleLabel(module) },
    auditActionLabel(action) { return getAuditActionLabel(action) },
    roleLabel(role) { return getRoleMeta(role).label }
  }
}
</script>

<style scoped>
.admin-console-page { display: grid; gap: 20px; }
.governance-tabs { min-width: 0; }
.governance-tabs ::v-deep .el-tabs__header { margin: 0 0 18px; }
.governance-tabs ::v-deep .el-tabs__item { height: 48px; padding: 0 24px; font-size: 15px; font-weight: 700; }
.settings-intro, .audit-guide, .dictionary-context { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-bottom: 14px; padding: 14px 16px; background: var(--ps-color-surface-soft); border: 1px solid var(--ps-color-border); border-radius: 12px; }
.settings-intro span, .audit-guide span, .dictionary-context span { margin-left: 8px; color: var(--ps-color-muted); font-size: 13px; }
.settings-grid { display: grid; gap: 14px; }
.setting-card { display: grid; grid-template-columns: minmax(260px, 1fr) minmax(320px, 1fr); align-items: center; gap: 28px; padding: 20px; background: var(--ps-color-surface); border: 1px solid var(--ps-color-border); border-radius: var(--ps-radius-md); }
.setting-card h2 { margin: 0; color: var(--ps-color-text); font-size: 18px; }
.setting-card p { margin: 7px 0; color: var(--ps-color-muted); line-height: 1.6; }
.setting-card code, .dictionary-context code, .label-with-code code { color: var(--ps-color-muted); font-size: 12px; overflow-wrap: anywhere; }
.setting-card__control { display: grid; justify-items: start; gap: 12px; }
.setting-card__control ::v-deep .el-textarea, .setting-card__control ::v-deep .el-input { width: 100%; }
.empty-state { padding: 42px; text-align: center; color: var(--ps-color-muted); background: var(--ps-color-surface); border-radius: var(--ps-radius-md); }
.dictionary-context, .audit-guide { margin: 4px 0 16px; }
.dictionary-context > div { display: flex; align-items: baseline; flex-wrap: wrap; gap: 8px; }
.label-with-code { display: grid; gap: 4px; }
.label-with-code strong { color: var(--ps-color-text); }
.technical-details { color: var(--ps-color-muted); font-size: 12px; }
.technical-details summary { cursor: pointer; list-style-position: inside; }
.technical-details code { display: block; margin-top: 4px; overflow-wrap: anywhere; }
.audit-technical-details { max-width: 220px; }
@media (max-width: 900px) { .setting-card { grid-template-columns: 1fr; } }
@media (max-width: 640px) { .settings-intro, .audit-guide, .dictionary-context { align-items: flex-start; flex-direction: column; } .settings-intro span, .audit-guide span, .dictionary-context span { display: block; margin: 4px 0 0; } }
</style>
