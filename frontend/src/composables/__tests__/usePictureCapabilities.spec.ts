import { beforeEach, expect, it, vi } from 'vitest'
const request = vi.hoisted(() => vi.fn())
vi.mock('@/request', () => ({ default: request }))
beforeEach(() => { vi.resetModules(); request.mockReset() })

it('coalesces card requests and keeps disabled gates explicit', async () => {
  request.mockResolvedValue({ code: 0, data: { privateStorageConfigured: false, externalImageSearchEnabled: false } })
  const { loadPictureCapabilities } = await import('../usePictureCapabilities')
  const first = loadPictureCapabilities(), second = loadPictureCapabilities()
  expect(first).toBe(second)
  expect(await first).toEqual({ privateStorageConfigured: false, externalImageSearchEnabled: false })
  await loadPictureCapabilities()
  expect(request).toHaveBeenCalledTimes(1)
})
it('fails closed on incomplete responses and permits a later retry', async () => {
  request.mockResolvedValueOnce({ code: 0, data: {} }).mockResolvedValueOnce({ code: 0, data: { privateStorageConfigured: true, externalImageSearchEnabled: false } })
  const { loadPictureCapabilities } = await import('../usePictureCapabilities')
  await expect(loadPictureCapabilities()).rejects.toThrow('无法确认')
  expect((await loadPictureCapabilities()).privateStorageConfigured).toBe(true)
})
