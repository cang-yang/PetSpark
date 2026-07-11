jest.mock('@/api/pets', () => ({
  listPets: jest.fn(),
  getPet: jest.fn(),
  createMyPet: jest.fn(),
}))
jest.mock('@/api/health', () => ({
  listHealthRecords: jest.fn(),
  createHealthRecord: jest.fn(),
  reviseHealthRecord: jest.fn(),
  eraseHealthRecord: jest.fn(),
}))
jest.mock('@/api/adoption', () => ({
  listAdoptablePets: jest.fn(),
  createAdoptionApplication: jest.fn(),
  listMyAdoptions: jest.fn(),
  getAdoption: jest.fn(),
  withdrawAdoption: jest.fn(),
}))
jest.mock('@/api/boarding', () => ({
  createBooking: jest.fn(),
  queryAvailability: jest.fn(),
  listMyBookings: jest.fn(),
  cancelBooking: jest.fn(),
}))
jest.mock('@/api/service', () => ({ listServiceItems: jest.fn() }))
jest.mock('@/api/training', () => ({ listTrainingItems: jest.fn() }))
jest.mock('@/api/catalog', () => ({ listGoods: jest.fn() }))
jest.mock('@/api/orders', () => ({
  listMyOrders: jest.fn(),
  getOrder: jest.fn(),
  cancelOrder: jest.fn(),
}))

import PetsView from '@/views/PetsView.vue'
import MyPetsView from '@/views/MyPetsView.vue'
import PetHealthView from '@/views/PetHealthView.vue'
import AdoptablePetsView from '@/views/AdoptablePetsView.vue'
import MyAdoptionsView from '@/views/MyAdoptionsView.vue'
import BoardingNewView from '@/views/BoardingNewView.vue'
import MyBoardingsView from '@/views/MyBoardingsView.vue'
import ServiceListView from '@/views/ServiceListView.vue'
import TrainingServiceListView from '@/views/TrainingServiceListView.vue'
import BeautyListView from '@/views/BeautyListView.vue'
import MedicalListView from '@/views/MedicalListView.vue'
import GoodsListView from '@/views/GoodsListView.vue'
import MyOrdersView from '@/views/MyOrdersView.vue'

const allPages = [
  PetsView,
  MyPetsView,
  PetHealthView,
  AdoptablePetsView,
  MyAdoptionsView,
  BoardingNewView,
  MyBoardingsView,
  ServiceListView,
  TrainingServiceListView,
  BeautyListView,
  MedicalListView,
  GoodsListView,
  MyOrdersView,
]

const listPages = allPages.filter((view) => view !== BoardingNewView)

describe('member business views UI contract', () => {
  test.each(allPages.map((view) => [view.name, view]))(
    '%s uses the shared page header',
    (_name, view) => {
      expect(view.components).toEqual(
        expect.objectContaining({ PageHeader: expect.any(Object) })
      )
    }
  )

  test.each(listPages.map((view) => [view.name, view]))(
    '%s provides unified loading, empty and error states',
    (_name, view) => {
      expect(view.components).toEqual(
        expect.objectContaining({
          LoadingState: expect.any(Object),
          EmptyState: expect.any(Object),
          ErrorState: expect.any(Object),
        })
      )
    }
  )

  it('uses domain cards for pet, service and order listings', () => {
    expect(PetsView.components).toEqual(
      expect.objectContaining({ PetCard: expect.any(Object) })
    )
    expect(MyPetsView.components).toEqual(
      expect.objectContaining({ PetCard: expect.any(Object) })
    )
    expect(AdoptablePetsView.components).toEqual(
      expect.objectContaining({ PetCard: expect.any(Object) })
    )
    for (const view of [
      ServiceListView,
      TrainingServiceListView,
      BeautyListView,
      MedicalListView,
    ]) {
      expect(view.components).toEqual(
        expect.objectContaining({ ServiceCard: expect.any(Object) })
      )
    }
    expect(MyOrdersView.components).toEqual(
      expect.objectContaining({ OrderStatusCard: expect.any(Object) })
    )
  })
})
