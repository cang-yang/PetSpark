const AUTH_PATHS = new Set(['/login', '/register', '/forgot-password'])
const PUBLIC_PATHS = new Set(['/', ...AUTH_PATHS])

export function inferLayout(path = '') {
  if (AUTH_PATHS.has(path)) return 'auth'
  if (path === '/admin' || path.startsWith('/admin/')) return 'admin'
  return 'public'
}

export function withLayoutMeta(routes = []) {
  return routes.map((route) => ({
    ...route,
    meta: {
      ...(route.meta || {}),
      layout: route.meta && route.meta.layout ? route.meta.layout : inferLayout(route.path),
      requiresAuth: route.meta && typeof route.meta.requiresAuth === 'boolean'
        ? route.meta.requiresAuth
        : !PUBLIC_PATHS.has(route.path)
    }
  }))
}
