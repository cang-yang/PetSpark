import http from '@/api/http'
import {
  listAdoptablePets,
  createAdoptionApplication,
  listMyAdoptions,
  getAdoption,
  withdrawAdoption,
  listAdminAdoptions,
  decideAdoption,
  handoverAdoption
} from '@/api/adoption'

jest.mock('@/api/http', () => ({
  get: jest.fn(),
  post: jest.fn(),
  put: jest.fn(),
  patch: jest.fn()
}))

describe('adoption API', () => {
  beforeEach(() => {
    Object.values(http).forEach(fn => fn.mockReset())
  })

  it('calls adoptable pets list endpoint', () => {
    listAdoptablePets({ keyword: '橘', species: 'CAT', page: 1, size: 10 })
    expect(http.get).toHaveBeenCalledWith('/api/v1/pets/adoptable', { params: { keyword: '橘', species: 'CAT', page: 1, size: 10 } })
  })

  it('attaches Idempotency-Key header on create', () => {
    const payload = { petId: 'p-1', statement: '我希望领养', profileSnapshot: '家有院子' }
    createAdoptionApplication(payload, 'idem-abc')
    expect(http.post).toHaveBeenCalledWith('/api/v1/adoptions', payload, { headers: { 'Idempotency-Key': 'idem-abc' } })
  })

  it('omits header when no idempotency key', () => {
    const payload = { petId: 'p-1', statement: '我希望领养' }
    createAdoptionApplication(payload)
    expect(http.post).toHaveBeenCalledWith('/api/v1/adoptions', payload, { headers: {} })
  })

  it('calls member adoption endpoints', () => {
    listMyAdoptions({ status: 'PENDING', page: 1, size: 10 })
    getAdoption('a-1')
    withdrawAdoption('a-1', { reason: '暂时不方便', version: 3 })

    expect(http.get).toHaveBeenCalledWith('/api/v1/adoptions', { params: { status: 'PENDING', page: 1, size: 10 } })
    expect(http.get).toHaveBeenCalledWith('/api/v1/adoptions/a-1')
    expect(http.post).toHaveBeenCalledWith('/api/v1/adoptions/a-1/withdraw', { reason: '暂时不方便', version: 3 })
  })

  it('calls admin adoption decision endpoint', () => {
    decideAdoption('a-1', { decision: 'APPROVED', note: '条件合适', version: 4 })
    expect(http.post).toHaveBeenCalledWith('/api/v1/admin/adoptions/a-1/decision', { decision: 'APPROVED', note: '条件合适', version: 4 })
  })

  it('calls admin adoption list endpoint', () => {
    listAdminAdoptions({ status: 'PENDING', keyword: 'ADOPT', page: 1, size: 10 })
    expect(http.get).toHaveBeenCalledWith('/api/v1/admin/adoptions', { params: { status: 'PENDING', keyword: 'ADOPT', page: 1, size: 10 } })
  })

  it('calls handover endpoint', () => {
    handoverAdoption('a-1', { result: 'SUCCESS', note: '已交接', version: 5 })
    expect(http.post).toHaveBeenCalledWith('/api/v1/adoptions/a-1/handover', { result: 'SUCCESS', note: '已交接', version: 5 })
  })
})
