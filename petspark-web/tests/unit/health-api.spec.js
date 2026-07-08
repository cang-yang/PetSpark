import http from '@/api/http'
import { listHealthRecords, createHealthRecord, reviseHealthRecord, eraseHealthRecord } from '@/api/health'

jest.mock('@/api/http', () => ({
  get: jest.fn(),
  post: jest.fn(),
  put: jest.fn(),
  patch: jest.fn(),
  delete: jest.fn()
}))

beforeEach(() => jest.clearAllMocks())

test('health API wraps list, create, revise and erase endpoints', () => {
  listHealthRecords('pet-1', { page: 1, recordType: 'VACCINATION' })
  expect(http.get).toHaveBeenCalledWith('/api/v1/pets/pet-1/health-records', { params: { page: 1, recordType: 'VACCINATION' } })

  createHealthRecord('pet-1', { recordType: 'CHECKUP', occurredOn: '2026-01-01', summary: '体检' })
  expect(http.post).toHaveBeenCalledWith('/api/v1/pets/pet-1/health-records', { recordType: 'CHECKUP', occurredOn: '2026-01-01', summary: '体检' })

  reviseHealthRecord('rec-1', { summary: '修订' })
  expect(http.post).toHaveBeenCalledWith('/api/v1/health-records/rec-1/revisions', { summary: '修订' })

  eraseHealthRecord('rec-1', { reason: '撤回授权' })
  expect(http.delete).toHaveBeenCalledWith('/api/v1/health-records/rec-1/content', { data: { reason: '撤回授权' } })
})