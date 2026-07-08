import http from './http'

export function uploadImage(file, businessType, onUploadProgress) {
  const body = new FormData()
  body.append('file', file)
  body.append('businessType', businessType)
  return http.post('/api/v1/files/images', body, { onUploadProgress })
}

export function confirmFile(fileId) {
  return http.post(`/api/v1/files/${fileId}/confirm`)
}
