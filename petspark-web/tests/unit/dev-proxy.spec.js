describe('Vue development proxy', () => {
  test('keeps API and actuator requests same-origin during local development', () => {
    const config = require('../../vue.config')

    expect(config.devServer.proxy['/api'].target).toBe('http://localhost:8080')
    expect(config.devServer.proxy['/actuator'].target).toBe('http://localhost:8080')
    expect(config.devServer.proxy['/api'].changeOrigin).toBe(true)
  })

  test('documents an empty API base URL so requests do not bypass the proxy', () => {
    const fs = require('fs')
    const path = require('path')
    const example = fs.readFileSync(path.resolve(__dirname, '../../.env.example'), 'utf8')

    expect(example).toMatch(/^VUE_APP_API_BASE_URL=$/m)
    expect(example).not.toContain('VUE_APP_API_BASE_URL=http://localhost:8080')
  })
})
