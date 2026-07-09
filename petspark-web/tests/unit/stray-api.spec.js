import http from '@/api/http'
import {
  assignStrayClue,
  createStrayClue,
  getAdminStrayClue,
  getMyStrayClue,
  listAdminStrayClues,
  listMyStrayClues,
  transitionStrayClue
} from '@/api/stray'

jest.mock('@/api/http', () => ({
  get: jest.fn(),
  post: jest.fn(),
  put: jest.fn(),
  patch: jest.fn()
}))

describe('stray API', () => {
  beforeEach(() => {
    Object.values(http).forEach(fn => fn.mockReset())
  })

  it('attaches Idempotency-Key header on clue create', () => {
    const payload = { animalType: 'CAT', location: '东门', description: '疑似受伤', imageFileIds: ['f-1'] }
    createStrayClue(payload, 'idem-stray')
    expect(http.post).toHaveBeenCalledWith('/api/v1/stray-clues', payload, { headers: { 'Idempotency-Key': 'idem-stray' } })
  })

  it('omits header when no idempotency key', () => {
    const payload = { animalType: 'DOG', location: '操场', description: '需要救助' }
    createStrayClue(payload)
    expect(http.post).toHaveBeenCalledWith('/api/v1/stray-clues', payload, { headers: {} })
  })

  it('calls member clue endpoints', () => {
    listMyStrayClues({ status: 'SUBMITTED', page: 1, size: 10 })
    getMyStrayClue('s-1')

    expect(http.get).toHaveBeenCalledWith('/api/v1/stray-clues/mine', { params: { status: 'SUBMITTED', page: 1, size: 10 } })
    expect(http.get).toHaveBeenCalledWith('/api/v1/stray-clues/s-1')
  })

  it('calls admin clue endpoints', () => {
    listAdminStrayClues({ keyword: '花坛', status: 'ASSIGNED', page: 1, size: 20 })
    getAdminStrayClue('s-1')
    assignStrayClue('s-1', { assignedUserId: 'u-admin', note: '安排救助', version: 1 })
    transitionStrayClue('s-1', { status: 'RESOLVED', note: '已安置', handoffNote: '可建档', version: 2 })

    expect(http.get).toHaveBeenCalledWith('/api/v1/admin/stray-clues', { params: { keyword: '花坛', status: 'ASSIGNED', page: 1, size: 20 } })
    expect(http.get).toHaveBeenCalledWith('/api/v1/admin/stray-clues/s-1')
    expect(http.post).toHaveBeenCalledWith('/api/v1/admin/stray-clues/s-1/assign', { assignedUserId: 'u-admin', note: '安排救助', version: 1 })
    expect(http.post).toHaveBeenCalledWith('/api/v1/admin/stray-clues/s-1/transition', { status: 'RESOLVED', note: '已安置', handoffNote: '可建档', version: 2 })
  })
})
