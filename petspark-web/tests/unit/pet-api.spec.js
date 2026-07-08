import http from '@/api/http'
import { listPets, createMyPet, listBreeds, listAdminPets, createAdminBreed, updateAdminPetStatus } from '@/api/pets'

jest.mock('@/api/http', () => ({ get: jest.fn(), post: jest.fn(), put: jest.fn(), patch: jest.fn(), delete: jest.fn() }))

beforeEach(() => jest.clearAllMocks())

test('pet API wraps public, mine, admin and breed endpoints', () => {
  listPets({ page: 1 })
  expect(http.get).toHaveBeenCalledWith('/api/v1/pets', { params: { page: 1 } })
  createMyPet({ name: 'Mimi' })
  expect(http.post).toHaveBeenCalledWith('/api/v1/pets/mine', { name: 'Mimi' })
  listBreeds({ species: 'CAT' })
  expect(http.get).toHaveBeenCalledWith('/api/v1/breeds', { params: { species: 'CAT' } })
  listAdminPets({ publicStatus: 'PUBLISHED' })
  expect(http.get).toHaveBeenCalledWith('/api/v1/admin/pets', { params: { publicStatus: 'PUBLISHED' } })
  createAdminBreed({ name: 'Corgi' })
  expect(http.post).toHaveBeenCalledWith('/api/v1/admin/breeds', { name: 'Corgi' })
  updateAdminPetStatus('p1', { adoptionStatus: 'ADOPTING', version: 1 })
  expect(http.patch).toHaveBeenCalledWith('/api/v1/admin/pets/p1/status', { adoptionStatus: 'ADOPTING', version: 1 })
})
