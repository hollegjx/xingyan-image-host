import { afterEach, describe, expect, it, vi } from 'vitest'
import { editingBlob, editingSource, taskPending } from '../aiEditing'

describe('AI image client boundaries', () => {
  afterEach(() => vi.unstubAllGlobals())
  it('only genuine in-progress states keep polling', () => {
    expect(taskPending('RUNNING')).toBe(true)
    expect(taskPending('SUBMITTING')).toBe(true)
    expect(taskPending('QUEUED')).toBe(true)
    expect(taskPending('APPLYING')).toBe(true)
    for (const state of ['UNKNOWN', 'FAILED', 'APPLIED', 'APPLY_UNKNOWN', undefined]) expect(taskPending(state)).toBe(false)
  })
  it('rejects foreign blob destinations before fetching', async () => {
    const fetch = vi.fn(); vi.stubGlobal('fetch', fetch)
    await expect(editingBlob('https://untrusted.invalid/private')).rejects.toThrow('不可信')
    expect(fetch).not.toHaveBeenCalled()
  })
  it('sends cookies only to same origin and never follows redirects', async () => {
    const fetch = vi.fn().mockResolvedValue({ ok: true, headers: new Headers(), blob: async () => new Blob(['test']) })
    vi.stubGlobal('fetch', fetch)
    await editingBlob('/api/picture/editing/source?id=1')
    expect(fetch).toHaveBeenCalledWith(expect.any(URL), expect.objectContaining({ credentials: 'same-origin', redirect: 'error', cache: 'no-store' }))
  })
  it('does not treat a JSON business error as an image', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, headers: new Headers({ 'content-type': 'application/json' }), json: async () => ({ message: '请先登录' }) }))
    await expect(editingBlob('/api/picture/editing/source?id=1')).rejects.toThrow('请先登录')
  })
  it('keeps long IDs as strings', () => {
    expect(editingSource('9007199254740999')).toContain('id=9007199254740999')
  })
})
