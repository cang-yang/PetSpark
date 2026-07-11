import axios from 'axios'
import store from '@/store'

const http = axios.create({
  baseURL: process.env.VUE_APP_API_BASE_URL || '',
  timeout: 10000,
  withCredentials: true
})

const refreshClient = axios.create({
  baseURL: process.env.VUE_APP_API_BASE_URL || '',
  timeout: 10000,
  withCredentials: true
})

let refreshPromise = null

http.interceptors.request.use((config) => {
  const token = store.state.accessToken
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => response.data,
  async (error) => {
    const original = error.config
    const canRefresh = error.response && error.response.status === 401 &&
      original && !original._petsparkRetried &&
      !original.url.startsWith('/api/v1/auth/') && store.state.accessToken
    if (canRefresh) {
      original._petsparkRetried = true
      try {
        if (!refreshPromise) {
          refreshPromise = refreshClient.post('/api/v1/auth/refresh')
            .then((response) => store.dispatch('saveLogin', response.data.data))
            .finally(() => { refreshPromise = null })
        }
        await refreshPromise
        return http(original)
      } catch (refreshError) {
        await store.dispatch('logout')
      }
    }
    const authenticationRequired = error.response && error.response.status === 401 &&
      original && !original.url.startsWith('/api/v1/auth/')
    if (authenticationRequired && typeof window !== 'undefined') {
      window.dispatchEvent(new CustomEvent('petspark:auth-required', {
        detail: { redirect: `${window.location.pathname}${window.location.search}` }
      }))
    }
    const body = error.response && error.response.data
    const message = body && body.message ? body.message : '请求失败，请稍后重试'
    return Promise.reject(new Error(message))
  }
)

export default http
