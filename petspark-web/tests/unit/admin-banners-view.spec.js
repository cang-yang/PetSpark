import { shallowMount } from '@vue/test-utils'
import AdminBannersView from '@/views/AdminBannersView.vue'
import { createBanner, deleteBanner, listAdminBanners, updateBannerOrder, updateBannerStatus } from '@/api/banner'

jest.mock('@/api/banner', () => ({
  createBanner: jest.fn(),
  deleteBanner: jest.fn(),
  listAdminBanners: jest.fn(),
  updateBanner: jest.fn(),
  updateBannerOrder: jest.fn(),
  updateBannerStatus: jest.fn()
}))

describe('AdminBannersView', () => {
  beforeEach(() => {
    jest.clearAllMocks()
    listAdminBanners.mockResolvedValue({
      data: {
        items: [{ id: 'b-1', title: '首页活动', status: 'DRAFT', sortOrder: 2, targetUrl: '/goods', version: 1 }],
        page: 1,
        size: 10,
        total: 1
      }
    })
    createBanner.mockResolvedValue({ data: { id: 'b-2', title: '新横幅', status: 'ACTIVE', sortOrder: 1, version: 0 } })
    updateBannerStatus.mockResolvedValue({ data: { id: 'b-1', title: '首页活动', status: 'ACTIVE', sortOrder: 2, version: 2 } })
    updateBannerOrder.mockResolvedValue({ data: { id: 'b-1', title: '首页活动', status: 'ACTIVE', sortOrder: 1, version: 3 } })
    deleteBanner.mockResolvedValue({ data: null })
  })

  it('loads backend banners and sends versioned actions', async () => {
    const wrapper = shallowMount(AdminBannersView, {
      mocks: { $message: { success: jest.fn(), error: jest.fn() } },
      stubs: ['el-button', 'el-input', 'el-table', 'el-table-column', 'el-select', 'el-option', 'el-tag', 'el-pagination', 'el-dialog', 'el-form', 'el-form-item', 'el-date-picker']
    })
    await flush()

    expect(listAdminBanners).toHaveBeenCalledWith({ keyword: undefined, status: undefined, page: 1, size: 10 })

    await wrapper.vm.changeStatus(wrapper.vm.banners[0], 'ACTIVE')
    expect(updateBannerStatus).toHaveBeenCalledWith('b-1', { status: 'ACTIVE', version: 1 })

    await wrapper.vm.move(wrapper.vm.banners[0], -1)
    expect(updateBannerOrder).toHaveBeenCalledWith('b-1', { sortOrder: 1, version: 2 })

    await wrapper.vm.remove(wrapper.vm.banners[0])
    expect(deleteBanner).toHaveBeenCalledWith('b-1', 3)
  })

  it('creates a banner with normalized optional fields', async () => {
    const wrapper = shallowMount(AdminBannersView, {
      mocks: { $message: { success: jest.fn(), error: jest.fn() } },
      stubs: ['el-button', 'el-input', 'el-table', 'el-table-column', 'el-select', 'el-option', 'el-tag', 'el-pagination', 'el-dialog', 'el-form', 'el-form-item', 'el-date-picker']
    })
    await flush()

    wrapper.vm.openCreate()
    Object.assign(wrapper.vm.form, { title: '新横幅', imageUrl: 'https://example.com/banner.png', status: 'ACTIVE', sortOrder: 1 })
    await wrapper.vm.save()

    expect(createBanner).toHaveBeenCalledWith({
      title: '新横幅',
      subtitle: null,
      imageUrl: 'https://example.com/banner.png',
      targetType: null,
      targetUrl: null,
      status: 'ACTIVE',
      sortOrder: 1,
      startsAt: null,
      endsAt: null,
      version: 0
    })
  })
})

function flush() {
  return new Promise(resolve => setTimeout(resolve, 0))
}
