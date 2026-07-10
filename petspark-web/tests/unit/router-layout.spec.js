import { inferLayout, withLayoutMeta } from '@/router/layout'

describe('route layout metadata', () => {
  it.each([
    ['/', 'public'],
    ['/login', 'auth'],
    ['/register', 'auth'],
    ['/forgot-password', 'auth'],
    ['/admin/users', 'admin'],
    ['/my/pets', 'public']
  ])('maps %s to the %s layout', (path, expected) => {
    expect(inferLayout(path)).toBe(expected)
  })

  it('decorates routes without mutating their existing metadata', () => {
    const source = [{ path: '/admin/pets', meta: { permission: 'pet:read' } }]
    const decorated = withLayoutMeta(source)

    expect(decorated[0].meta).toEqual({ permission: 'pet:read', layout: 'admin' })
    expect(source[0].meta).toEqual({ permission: 'pet:read' })
  })

  it('keeps an explicitly selected layout', () => {
    const decorated = withLayoutMeta([{ path: '/special', meta: { layout: 'auth' } }])
    expect(decorated[0].meta.layout).toBe('auth')
  })
})
