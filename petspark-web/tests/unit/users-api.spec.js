import http from '@/api/http'
import { getMyProfile, updateMyProfile } from '@/api/users'

jest.mock('@/api/http', () => ({
  get: jest.fn(),
  put: jest.fn()
}))

describe('users API', () => {
  beforeEach(() => {
    http.get.mockReset()
    http.put.mockReset()
  })

  it('loads current user profile', () => {
    getMyProfile()
    expect(http.get).toHaveBeenCalledWith('/api/v1/users/me')
  })

  it('updates current user profile with whitelist payload', () => {
    const payload = {
      nickname: '阳阳',
      phone: '13800138000',
      avatarFileId: 'file-1',
      bio: '喜欢猫猫狗狗',
      version: 3
    }
    updateMyProfile(payload)
    expect(http.put).toHaveBeenCalledWith('/api/v1/users/me', payload)
  })
})
