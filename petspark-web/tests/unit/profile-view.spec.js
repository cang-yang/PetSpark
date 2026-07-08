import { createLocalVue, shallowMount } from '@vue/test-utils'
import ProfileView from '@/views/ProfileView.vue'
import { getMyProfile, updateMyProfile } from '@/api/users'

jest.mock('@/api/users', () => ({
  getMyProfile: jest.fn(),
  updateMyProfile: jest.fn()
}))

describe('ProfileView', () => {
  const localVue = createLocalVue()
  localVue.directive('loading', {})
  const stubs = {
    'el-alert': true,
    'el-card': true,
    'el-form': true,
    'el-form-item': true,
    'el-input': true,
    'el-button': true,
    ImageUploader: true
  }

  const profile = {
    id: 'u-1',
    username: 'yang',
    email: 'yang@example.com',
    nickname: '阳阳',
    avatarFileId: 'file-1',
    avatarUrl: '/api/v1/files/file-1',
    phoneMasked: '138****8000',
    bio: '喜欢猫猫狗狗',
    version: 2
  }

  beforeEach(() => {
    getMyProfile.mockReset()
    updateMyProfile.mockReset()
    getMyProfile.mockResolvedValue({ data: profile })
    updateMyProfile.mockResolvedValue({ data: { ...profile, nickname: '新昵称', version: 3 } })
  })

  it('loads profile into form on creation', async () => {
    const wrapper = shallowMount(ProfileView, {
      localVue,
      stubs,
      mocks: { $store: { commit: jest.fn(), state: { user: { nickname: '阳阳' } } } }
    })

    await flush()

    expect(getMyProfile).toHaveBeenCalled()
    expect(wrapper.vm.form.nickname).toBe('阳阳')
    expect(wrapper.vm.form.phoneMasked).toBe('138****8000')
    expect(wrapper.vm.form.version).toBe(2)
  })

  it('confirms avatar and saves whitelist fields', async () => {
    const commit = jest.fn()
    const wrapper = shallowMount(ProfileView, {
      localVue,
      stubs,
      mocks: { $store: { commit, state: { user: { nickname: '阳阳' } } } }
    })
    await flush()
    wrapper.vm.$refs.avatarUploader = { confirm: jest.fn().mockResolvedValue({}) }
    wrapper.setData({
      form: {
        ...wrapper.vm.form,
        nickname: '新昵称',
        phone: '13800138000',
        avatarFileId: 'file-1',
        bio: '新简介',
        version: 2
      }
    })

    await wrapper.vm.save()

    expect(wrapper.vm.$refs.avatarUploader.confirm).toHaveBeenCalled()
    expect(updateMyProfile).toHaveBeenCalledWith({
      nickname: '新昵称',
      phone: '13800138000',
      avatarFileId: 'file-1',
      bio: '新简介',
      version: 2
    })
    expect(commit).toHaveBeenCalledWith('setUser', expect.objectContaining({ nickname: '新昵称' }))
    expect(wrapper.vm.message).toBe('个人资料已保存')
  })
})

function flush() {
  return new Promise(resolve => setTimeout(resolve))
}
