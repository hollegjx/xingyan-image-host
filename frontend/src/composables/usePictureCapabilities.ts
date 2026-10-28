import { onMounted, ref, readonly } from 'vue'
import request from '@/request'

interface Capabilities { privateStorageConfigured: boolean; externalImageSearchEnabled: boolean }
const state = ref<Capabilities>({ privateStorageConfigured: false, externalImageSearchEnabled: false })
const error = ref('')
let fetchedAt = 0
let pending: Promise<Capabilities> | undefined

export function loadPictureCapabilities(): Promise<Capabilities> {
  if (pending) return pending
  if (Date.now() - fetchedAt < 30000) return Promise.resolve(state.value)
  pending = request<{ code?: number; data?: Capabilities; message?: string }>('/api/picture/capabilities')
    .then((response) => {
      if (response.code !== 0 || !response.data || typeof response.data.privateStorageConfigured !== 'boolean'
        || typeof response.data.externalImageSearchEnabled !== 'boolean') throw new Error('无法确认服务配置，请稍后重试')
      state.value = response.data; error.value = ''; fetchedAt = Date.now()
      return response.data
    }).catch((cause: unknown) => {
      state.value = { privateStorageConfigured: false, externalImageSearchEnabled: false }
      error.value = cause instanceof Error ? cause.message : '无法确认服务配置，请稍后重试'
      throw cause
    }).finally(() => { pending = undefined })
  return pending
}

export function usePictureCapabilities() {
  onMounted(() => { void loadPictureCapabilities().catch(() => undefined) })
  return { capabilities: readonly(state), capabilityError: readonly(error) }
}
