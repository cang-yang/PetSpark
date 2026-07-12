import { decodeJwtAuthorities, hasManagementAccess } from '@/auth/authorities'

function token(authorities) {
  const payload = window.btoa(JSON.stringify({ authorities }))
    .replace(/=/g, '')
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
  return `header.${payload}.signature`
}

describe('navigation authorities', () => {
  it('decodes authorities from the signed access token payload', () => {
    expect(decodeJwtAuthorities(token(['role:read', 'user:update']))).toEqual(['role:read', 'user:update'])
  })

  it('keeps the member baseline out of management navigation', () => {
    expect(hasManagementAccess(['pet:read', 'file:upload', 'user:profile'])).toBe(false)
    expect(hasManagementAccess(['pet:read', 'role:read'])).toBe(true)
  })

  it('fails closed for malformed tokens', () => {
    expect(decodeJwtAuthorities('not-a-token')).toEqual([])
  })
})
