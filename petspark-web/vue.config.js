const backendTarget = process.env.PETSPARK_DEV_API_TARGET || 'http://localhost:8080'

module.exports = {
  devServer: {
    proxy: {
      '/api': {
        target: backendTarget,
        changeOrigin: true
      },
      '/actuator': {
        target: backendTarget,
        changeOrigin: true
      }
    }
  }
}
