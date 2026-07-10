import { createLocalVue, shallowMount } from '@vue/test-utils'
import ElementUI from 'element-ui'
import PetsView from '@/views/PetsView.vue'
import { listPets } from '@/api/pets'

jest.mock('@/api/pets', () => ({ listPets: jest.fn() }))
const flushPromises = () => new Promise((resolve) => setTimeout(resolve, 0))
const localVue = createLocalVue()
localVue.use(ElementUI)

test('PetsView renders returned pets and empty state', async () => {
  listPets.mockResolvedValueOnce({
    data: {
      data: {
        items: [{ id: 'p1', name: 'Mimi', adoptionStatus: 'AVAILABLE' }],
      },
    },
  })
  const wrapper = shallowMount(PetsView, { localVue })
  await flushPromises()
  await wrapper.vm.$nextTick()
  expect(wrapper.text()).toContain('Mimi')
  listPets.mockResolvedValueOnce({ data: { data: { items: [] } } })
  await wrapper.vm.load()
  await wrapper.vm.$nextTick()
  expect(wrapper.text()).toContain('暂无宠物')
})
