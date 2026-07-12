const MEMBER_AUTHORITIES = new Set(['pet:read', 'file:upload', 'user:profile'])

export function decodeJwtAuthorities(token) {
  if (!token || typeof token !== 'string') return []
  try {
    const payload = token.split('.')[1]
    if (!payload) return []
    const normalized = payload.replace(/-/g, '+').replace(/_/g, '/')
    const padded = normalized.padEnd(Math.ceil(normalized.length / 4) * 4, '=')
    const parsed = JSON.parse(window.atob(padded))
    return Array.isArray(parsed.authorities)
      ? parsed.authorities.filter((item) => typeof item === 'string')
      : []
  } catch (_) {
    return []
  }
}

export function hasManagementAccess(authorities) {
  return Array.isArray(authorities) && authorities.some((authority) => !MEMBER_AUTHORITIES.has(authority))
}
