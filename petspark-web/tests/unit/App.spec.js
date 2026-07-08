import { createLocalVue, shallowMount } from '@vue/test-utils'
import VueRouter from 'vue-router'
import App from '@/App.vue'

describe('App', () => {
  it('renders the PetSpark application shell', () => {
    const localVue = createLocalVue()
    localVue.use(VueRouter)
    const wrapper = shallowMount(App, {
      localVue,
      router: new VueRouter()
    })

    expect(wrapper.get('[data-testid="app-title"]').text()).toBe('PetSpark')
    expect(wrapper.find('router-view-stub').exists()).toBe(true)
  })
})
