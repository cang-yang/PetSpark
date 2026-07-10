import { inferRouteScene } from '@/ui/scene'

describe('route scene atmosphere', () => {
  it.each([
    ['/', 'home'],
    ['/my/pets', 'care'],
    ['/pets/health/12', 'care'],
    ['/pets', 'companion'],
    ['/adoptions', 'companion'],
    ['/boarding/new', 'service'],
    ['/goods', 'service'],
    ['/ai/chat', 'ai'],
    ['/admin/users', 'admin'],
    ['/unknown', 'care']
  ])('maps %s to the %s scene', (path, scene) => {
    expect(inferRouteScene(path)).toBe(scene)
  })
})
