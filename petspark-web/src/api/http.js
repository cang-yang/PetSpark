import axios from 'axios'
import store from '@/store'

const http = axios.create({
  baseURL: process.env.VUE_APP_API_BASE_URL || '',
  timeout: 10000
})

http.interceptors.request.use((config) => {
  const token = store.state.accessToken
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => response.data,
  (error) => {
    const body = error.response && error.response.data
    const message = body && body.message ? body.message : '请求失败，请稍后重试'
    return Promise.reject(new Error(message))
  }
)

export default http
