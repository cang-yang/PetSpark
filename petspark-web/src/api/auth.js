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

export function refreshSession() {
  return http.post('/api/v1/auth/refresh')
}

export function logout() {
  return http.post('/api/v1/auth/logout')
}

export function requestPasswordResetCode(payload) {
  return http.post('/api/v1/auth/password-reset-codes', payload)
}

export function resetPassword(payload) {
  return http.post('/api/v1/auth/password-resets', payload)
}
