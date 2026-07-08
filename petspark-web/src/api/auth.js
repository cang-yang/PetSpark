import http from './http'

export function issueCaptcha(clientHash) {
  return http.post('/api/v1/auth/captcha', { clientHash })
}

export function register(payload) {
  return http.post('/api/v1/auth/register', payload)
}

export function login(payload) {
  return http.post('/api/v1/auth/login', payload)
}
