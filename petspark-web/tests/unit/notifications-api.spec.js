import http from '@/api/http'
import {
  listNotifications,
  markNotificationRead,
  markAllNotificationsRead
} from '@/api/notifications'

jest.mock('@/api/http', () => ({
  get: jest.fn(),
  put: jest.fn()
}))

describe('notifications API', () => {
  beforeEach(() => {
    http.get.mockReset()
    http.put.mockReset()
  })

  it('lists own notifications with page, size and onlyUnread', () => {
    listNotifications({ page: 1, size: 10, onlyUnread: true })

    const [url, config] = http.get.mock.calls[0]
    expect(url).toBe('/api/v1/notifications')
    expect(config.params).toEqual({ page: 1, size: 10, onlyUnread: true })
  })

  it('marks a single notification read by id', () => {
    markNotificationRead('n-1')
    expect(http.put).toHaveBeenCalledWith('/api/v1/notifications/n-1/read')
  })

  it('marks all notifications read', () => {
    markAllNotificationsRead()
    expect(http.put).toHaveBeenCalledWith('/api/v1/notifications/read-all')
  })
})
