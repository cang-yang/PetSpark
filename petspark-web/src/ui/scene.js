const GROUPS = [
  { scene: 'admin', prefixes: ['/admin'] },
  { scene: 'ai', prefixes: ['/ai'] },
  { scene: 'care', prefixes: ['/my/pets', '/pets/health', '/profile', '/notifications'] },
  { scene: 'companion', prefixes: ['/pets', '/adoptions', '/my/adoptions', '/community', '/stray'] },
  { scene: 'service', prefixes: ['/boarding', '/my/boardings', '/services', '/training', '/beauty', '/medical', '/goods', '/orders', '/my/orders'] }
]

export function inferRouteScene(path = '') {
  if (path === '/') return 'home'
  const match = GROUPS.find(({ prefixes }) => prefixes.some((prefix) => path === prefix || path.startsWith(`${prefix}/`)))
  return match ? match.scene : 'care'
}
