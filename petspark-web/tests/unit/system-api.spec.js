import http from '@/api/http'
import {
  createDictItem,
  createDictType,
  listAuditLogs,
  listDictItems,
  listDictTypes,
  listSystemConfigs,
  updateDictItem,
  updateSystemConfig
} from '@/api/system'

jest.mock('@/api/http', () => ({
  get: jest.fn(),
  post: jest.fn(),
  put: jest.fn()
}))

describe('system API', () => {
  beforeEach(() => {
    http.get.mockReset()
    http.post.mockReset()
    http.put.mockReset()
  })

  it('wraps dictionary endpoints', () => {
    listDictTypes()
    createDictType({ code: 'demo', name: '演示' })
    listDictItems('demo')
    createDictItem('demo', { itemKey: 'A', itemLabel: 'A项', sortOrder: 1, status: 'ACTIVE' })
    updateDictItem('i-1', { itemLabel: 'B项', sortOrder: 2, status: 'ACTIVE', version: 1 })

    expect(http.get).toHaveBeenCalledWith('/api/v1/admin/dictionaries/types')
    expect(http.post).toHaveBeenCalledWith('/api/v1/admin/dictionaries/types', { code: 'demo', name: '演示' })
    expect(http.get).toHaveBeenCalledWith('/api/v1/admin/dictionaries/demo/items')
    expect(http.post).toHaveBeenCalledWith('/api/v1/admin/dictionaries/demo/items', {
      itemKey: 'A',
      itemLabel: 'A项',
      sortOrder: 1,
      status: 'ACTIVE'
    })
    expect(http.put).toHaveBeenCalledWith('/api/v1/admin/dictionaries/items/i-1', {
      itemLabel: 'B项',
      sortOrder: 2,
      status: 'ACTIVE',
      version: 1
    })
  })

  it('wraps config and audit endpoints', () => {
    listSystemConfigs()
    updateSystemConfig('site.notice', { configValue: '公告', valueType: 'STRING', version: 1 })
    listAuditLogs({ module: 'system', page: 1, size: 20 })

    expect(http.get).toHaveBeenCalledWith('/api/v1/admin/configs')
    expect(http.put).toHaveBeenCalledWith('/api/v1/admin/configs/site.notice', {
      configValue: '公告',
      valueType: 'STRING',
      version: 1
    })
    expect(http.get).toHaveBeenCalledWith('/api/v1/admin/audits', {
      params: { module: 'system', page: 1, size: 20 }
    })
  })
})
