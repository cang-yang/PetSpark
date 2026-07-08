import http from '@/api/http'
import { uploadImage, confirmFile } from '@/api/files'

jest.mock('@/api/http', () => ({ post: jest.fn() }))

describe('file API', () => {
  beforeEach(() => http.post.mockReset())

  it('uploads multipart image with business type and progress callback', () => {
    const file = new File(['image'], 'avatar.png', { type: 'image/png' })
    const onProgress = jest.fn()

    uploadImage(file, 'PROFILE_AVATAR', onProgress)

    const [url, body, config] = http.post.mock.calls[0]
    expect(url).toBe('/api/v1/files/images')
    expect(body.get('file')).toBe(file)
    expect(body.get('businessType')).toBe('PROFILE_AVATAR')
    expect(config.onUploadProgress).toBe(onProgress)
  })

  it('confirms a staged file', () => {
    confirmFile('file-1')
    expect(http.post).toHaveBeenCalledWith('/api/v1/files/file-1/confirm')
  })
})
