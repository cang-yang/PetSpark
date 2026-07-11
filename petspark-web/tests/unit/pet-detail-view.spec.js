import { createLocalVue, shallowMount } from '@vue/test-utils'
import PetDetailView from '@/views/PetDetailView.vue'
import { getPet } from '@/api/pets'

jest.mock('@/api/pets', () => ({ getPet: jest.fn() }))

const localVue = createLocalVue()

describe('PetDetailView', () => {
  beforeEach(() => jest.clearAllMocks())

  it('renders a rich adoptable pet profile and opens the adoption flow', async () => {
    getPet.mockResolvedValue({
      data: {
        id: 'pet-1', name: '豆包', species: 'DOG', breedName: '柯基', sex: 'MALE',
        birthDate: '2024-05-10', description: '喜欢和人一起散步。', color: '黄白',
        behaviorTraits: '亲人、活泼', sterilizationStatus: 'STERILIZED',
        trainingLevel: 'BASIC', specialNeeds: '控制零食摄入', registeredAt: '2026-07-01',
        adoptionStatus: 'AVAILABLE', ownershipType: 'PLATFORM', ownedByCurrentUser: false,
        images: [{ fileId: 'f-1', previewUrl: '/api/v1/files/f-1', coverFlag: true }]
      }
    })
    const push = jest.fn()
    const wrapper = shallowMount(PetDetailView, {
      localVue,
      mocks: { $route: { params: { id: 'pet-1' } }, $router: { push } },
      stubs: ['el-button', 'loading-state', 'error-state', 'pet-avatar', 'router-link']
    })
    await flush()
    await wrapper.vm.$nextTick()

    expect(getPet).toHaveBeenCalledWith('pet-1')
    expect(wrapper.text()).toContain('豆包')
    expect(wrapper.text()).toContain('亲人、活泼')
    expect(wrapper.text()).toContain('控制零食摄入')
    expect(wrapper.find('[data-testid="pet-adoption-cta"]').exists()).toBe(true)
    wrapper.vm.openAdoption()
    expect(push).toHaveBeenCalledWith({ name: 'adoptions', query: { petId: 'pet-1' } })
  })

  it('shows the health-record action only for the current user own pet', async () => {
    getPet.mockResolvedValue({
      data: { id: 'mine-1', name: '奶糖', species: 'CAT', adoptionStatus: 'NOT_FOR_ADOPTION', ownedByCurrentUser: true, images: [] }
    })
    const wrapper = shallowMount(PetDetailView, {
      localVue,
      mocks: { $route: { params: { id: 'mine-1' } }, $router: { push: jest.fn() } },
      stubs: ['el-button', 'loading-state', 'error-state', 'pet-avatar', 'router-link']
    })
    await flush()
    await wrapper.vm.$nextTick()

    expect(wrapper.find('[data-testid="pet-health-cta"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="pet-adoption-cta"]').exists()).toBe(false)
  })
})

function flush() {
  return new Promise(resolve => setTimeout(resolve, 0))
}
