import { afterEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import type { Router } from 'vue-router'
import { galleryOrigin, returnToGallery, safeGalleryPath } from '../galleryNavigation'
import { useNavigationStore } from '@/stores/useNavigationStore'
import { useLoginUserStore } from '@/stores/useLoginUserStore'

afterEach(() => window.history.replaceState({}, ''))

describe('safe gallery destinations', () => {
  it.each(['/', '/?category=自然#gallery', '/picture/mine?q=山&page=2', '/space/9007199254740993'])(
    'accepts %s',
    (path) => {
      expect(safeGalleryPath(path)).toBe(true)
    },
  )
  it.each([
    'https://example.com',
    '//example.com',
    '/\\example.com',
    '/user/login',
    '/space/mine',
    '/space/1/members',
    '/\n',
  ])('rejects %s', (path) => {
    expect(safeGalleryPath(path)).toBe(false)
  })
  it('returns through the existing history entry and keeps its query and scroll', () => {
    window.history.replaceState(
      { position: 8, xingyanEntry: { origin: { path: '/picture/mine?page=2', position: 6 } } },
      '',
    )
    const router = { go: vi.fn(), push: vi.fn() } as unknown as Router
    returnToGallery(router)
    expect(router.go).toHaveBeenCalledWith(-2)
    expect(router.push).not.toHaveBeenCalled()
  })
  it('uses a safe fallback for direct links and ignores a hostile origin', () => {
    window.history.replaceState(
      { position: 8, xingyanEntry: { origin: { path: '//example.com', position: 6 } } },
      '',
    )
    const router = { go: vi.fn(), push: vi.fn() } as unknown as Router
    expect(galleryOrigin()).toBeUndefined()
    returnToGallery(router, '/space/9007199254740993')
    expect(router.push).toHaveBeenCalledWith('/space/9007199254740993')
  })
})

describe('navigation-only state', () => {
  it('clones filter choices and clears them when the account changes', () => {
    setActivePinia(createPinia())
    const navigation = useNavigationStore()
    const user = useLoginUserStore()
    user.setLoginUser({ id: '1' })
    const filter = { tags: ['自然'], current: 2 }
    navigation.rememberList('home', filter)
    filter.tags.push('建筑')
    expect(navigation.readList('home')).toEqual({ tags: ['自然'], current: 2 })
    navigation.rememberSpace({ id: '9007199254740993', spaceType: 1 })
    navigation.rememberPicture({ id: '9007199254740994', spaceId: '9007199254740993' })
    expect(navigation.spaceTypes['9007199254740993']).toBe(1)
    user.clearLoginUser()
    expect(navigation.readList('home')).toBeUndefined()
    expect(navigation.spaceTypes).toEqual({})
    expect(navigation.pictureSpaces).toEqual({})
  })
})
