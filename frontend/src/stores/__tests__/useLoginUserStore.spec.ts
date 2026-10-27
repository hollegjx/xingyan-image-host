import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { getLoginUserUsingGet } from '@/service/api/userController'
import { useLoginUserStore } from '../useLoginUserStore'

vi.mock('@/service/api/userController', () => ({
  getLoginUserUsingGet: vi.fn(),
}))

describe('useLoginUserStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.mocked(getLoginUserUsingGet).mockReset()
  })

  it('keeps public navigation available when login lookup fails', async () => {
    vi.mocked(getLoginUserUsingGet).mockRejectedValue(new Error('backend unavailable'))
    const store = useLoginUserStore()

    await expect(store.fetchLoginUser()).resolves.toBeUndefined()
    expect(store.loginUser).toEqual({})
  })

  it('deduplicates concurrent login lookups', async () => {
    let resolveRequest: ((value: API.BaseResponseLoginUserVO_) => void) | undefined
    vi.mocked(getLoginUserUsingGet).mockImplementation(
      () => new Promise((resolve) => (resolveRequest = resolve)),
    )
    const store = useLoginUserStore()

    const first = store.fetchLoginUser()
    const second = store.fetchLoginUser()
    resolveRequest?.({ code: 0, data: { id: 1, userName: 'tester' } })
    await Promise.all([first, second])

    expect(getLoginUserUsingGet).toHaveBeenCalledTimes(1)
    expect(store.loginUser.id).toBe(1)
  })
})
