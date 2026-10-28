import request from '@/request'

export interface AiTask {
  id: string
  pictureId: string | number
  status: string
  message?: string
  resultUrl?: string
  createTime?: string
  expiresAt?: string
}
interface Response<T> { code?: number; data?: T; message?: string }
export interface AiCreate {
  pictureId: string | number
  requestId: string
  externalProcessingConsent: boolean
  xScale: number
  yScale: number
}
const root = '/api/picture/out_painting'
export const aiCapabilities = () => request<Response<{ enabled: boolean; privateEnabled: boolean }>>(`${root}/capabilities`)
export const createAiTask = (data: AiCreate) => request<Response<AiTask>>(`${root}/create_task`, { method: 'POST', data, timeout: 20000 })
export const getAiTask = (taskId: string) => request<Response<AiTask>>(`${root}/get_task`, { params: { taskId }, timeout: 20000 })
export const latestAiTask = (pictureId: string | number) => request<Response<AiTask>>(`${root}/latest`, { params: { pictureId } })
export const applyAiTask = (taskId: string) => request<Response<API.PictureVO>>(`${root}/apply`, { method: 'POST', params: { taskId }, timeout: 120000 })
export const recoverAiTask = (taskId: string) => request<Response<AiTask>>(`${root}/recover`, { method: 'POST', params: { taskId }, timeout: 120000 })

/** Authenticated binary request, accepting only same-origin application routes. Never follows redirects. */
export async function editingBlob(path: string, signal?: AbortSignal) {
  const url = new URL(path, window.location.origin)
  if (url.origin !== window.location.origin) throw new Error('图片读取地址不可信')
  const response = await fetch(url, { credentials: 'same-origin', redirect: 'error', signal, cache: 'no-store' })
  if (!response.ok) throw new Error('图片读取失败，请检查存储配置和登录状态')
  if (response.headers.get('content-type')?.includes('json')) {
    const error = await response.json()
    throw new Error(error.message || '图片读取失败')
  }
  const blob = await response.blob()
  if (!blob.size || blob.size > 10 * 1024 * 1024) throw new Error('图片文件大小不受支持')
  return blob
}
export function editingSource(id: string | number) {
  return `${import.meta.env.VITE_API_BASE_URL || ''}/api/picture/editing/source?id=${encodeURIComponent(id)}`
}
export const taskPending = (status?: string) => !!status && ['QUEUED', 'SUBMITTING', 'PENDING', 'RUNNING', 'SUSPENDED', 'APPLYING'].includes(status)
