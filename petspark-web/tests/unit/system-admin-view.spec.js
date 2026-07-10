import { createLocalVue, shallowMount } from '@vue/test-utils'
import SystemAdminView from '@/views/SystemAdminView.vue'
import {
  listAuditLogs,
  listDictItems,
  listDictTypes,
  listSystemConfigs,
  updateSystemConfig
} from '@/api/system'

jest.mock('@/api/system', () => ({
  listAuditLogs: jest.fn(),
  listDictItems: jest.fn(),
  listDictTypes: jest.fn(),
  listSystemConfigs: jest.fn(),
  updateSystemConfig: jest.fn()
}))

describe('SystemAdminView', () => {
  const localVue = createLocalVue()
  localVue.directive('loading', {})
  const stubs = {
    'el-button': true,
    'el-card': true,
    'el-col': true,
    'el-input': true,
    'el-option': true,
    'el-row': true,
    'el-select': true,
    'el-switch': true,
    'el-tabs': true,
    'el-tab-pane': true,
    'el-table': true,
    'el-table-column': true
  }

  beforeEach(() => {
    jest.clearAllMocks()
    listSystemConfigs.mockResolvedValue({
      data: [{ configKey: 'site.notice', configValue: '公告', valueType: 'STRING', version: 1 }]
    })
    listDictTypes.mockResolvedValue({ data: [{ code: 'pet_gender', name: '宠物性别' }] })
    listDictItems.mockResolvedValue({ data: [{ itemKey: 'MALE', itemLabel: '公' }] })
    listAuditLogs.mockResolvedValue({ data: { items: [{ id: 'a-1', module: 'system', action: 'update' }] } })
    updateSystemConfig.mockResolvedValue({
      data: { configKey: 'site.notice', configValue: '新公告', valueType: 'STRING', version: 2 }
    })
  })

  function mountView() {
    return shallowMount(SystemAdminView, {
      localVue,
      stubs,
      mocks: { $message: { success: jest.fn(), error: jest.fn() } }
    })
  }

  it('loads configs dictionaries and audits on creation', async () => {
    const wrapper = mountView()
    await flush()

    expect(listSystemConfigs).toHaveBeenCalled()
    expect(listDictTypes).toHaveBeenCalled()
    expect(listDictItems).toHaveBeenCalledWith('pet_gender')
    expect(listAuditLogs).toHaveBeenCalledWith({ module: undefined, result: undefined, page: 1, size: 20 })
    expect(wrapper.vm.configs[0].configKey).toBe('site.notice')
    expect(wrapper.vm.audits[0].id).toBe('a-1')
  })

  it('saves non-sensitive config with version', async () => {
    const wrapper = mountView()
    await flush()

    await wrapper.vm.saveConfig(wrapper.vm.configs[0])

    expect(updateSystemConfig).toHaveBeenCalledWith('site.notice', {
      configValue: '公告',
      valueType: 'STRING',
      description: '',
      version: 1
    })
    expect(wrapper.vm.configs[0].version).toBe(2)
  })

  it('maps configuration and dictionary values to readable Chinese labels', async () => {
    listSystemConfigs.mockResolvedValue({
      data: [
        { configKey: 'feature.registration.enabled', configValue: 'true', valueType: 'BOOLEAN', version: 1 },
        { configKey: 'site.notice', configValue: '公告', valueType: 'STRING', version: 1 }
      ]
    })
    listDictTypes.mockResolvedValue({ data: [{ code: 'PET_GENDER', name: 'Pet Gender' }] })
    listDictItems.mockResolvedValue({ data: [{ itemKey: 'MALE', itemLabel: 'Male' }] })

    const wrapper = mountView()
    await flush()

    expect(wrapper.vm.configMeta(wrapper.vm.configs[0]).label).toBe('开放用户注册')
    expect(wrapper.vm.booleanConfigValue(wrapper.vm.configs[0])).toBe(true)
    expect(wrapper.vm.dictTypeLabel(wrapper.vm.dictTypes[0])).toBe('宠物性别')
    expect(wrapper.vm.dictItemLabel(wrapper.vm.dictItems[0])).toBe('雄性')
  })

  it('localizes audit module, action and role while retaining technical codes', async () => {
    const wrapper = mountView()
    await flush()

    expect(wrapper.vm.auditModuleLabel('system')).toBe('系统设置')
    expect(wrapper.vm.auditActionLabel('update')).toBe('更新')
    expect(wrapper.vm.roleLabel('ADMIN')).toBe('平台管理员')
  })

  it('rolls a setting back to its persisted value when saving fails', async () => {
    updateSystemConfig.mockRejectedValueOnce(new Error('保存失败'))
    const wrapper = mountView()
    await flush()

    wrapper.vm.configs[0].configValue = '尚未保存的新公告'
    await wrapper.vm.saveConfig(wrapper.vm.configs[0])

    expect(wrapper.vm.configs[0].configValue).toBe('公告')
    expect(wrapper.vm.$message.error).toHaveBeenCalledWith('保存失败')
  })
})

function flush() {
  return new Promise(resolve => setTimeout(resolve, 0))
}
