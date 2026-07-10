describe('Vue development proxy', () => {
  test('keeps API and actuator requests same-origin during local development', () => {
    const config = require('../../vue.config')

    expect(config.devServer.proxy['/api'].target).toBe('http://localhost:8080')
    expect(config.devServer.proxy['/actuator'].target).toBe('http://localhost:8080')
    expect(config.devServer.proxy['/api'].changeOrigin).toBe(true)
  })
})
