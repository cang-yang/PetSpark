const AUTH_PATHS = new Set(['/login', '/register', '/forgot-password'])

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
      layout: route.meta && route.meta.layout ? route.meta.layout : inferLayout(route.path)
    }
  }))
}
