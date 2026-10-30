import request from '@/request'

export function submitPublicPicture(pictureId: string | number) {
  return request<API.BaseResponseLong_>('/api/picture/submit/public', {
    method: 'POST',
    data: { pictureId },
    timeout: 120000,
  })
}

export function generatePrivatePictureLink(pictureId: string | number, validHours: number) {
  return request<{ code?: number; message?: string; data?: { url: string; expiresAt: string } }>(
    '/api/picture/private/link',
    { method: 'POST', data: { pictureId, validHours } },
  )
}
