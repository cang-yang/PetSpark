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
})

function flush() {
  return new Promise(resolve => setTimeout(resolve, 0))
}
