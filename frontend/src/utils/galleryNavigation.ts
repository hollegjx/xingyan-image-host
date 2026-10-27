import type { Router, RouteLocationNormalized } from 'vue-router'

type GalleryOrigin = { path: string; position: number }
type Entry = { path: string; position: number; origin?: GalleryOrigin }
const entryKey = 'xingyanEntry'
const galleries = new Set(['home', 'my-pictures', 'space-detail'])
const picturePages = new Set(['picture-detail', 'add-picture', 'collaborate-picture'])

export function safeGalleryPath(path: unknown): path is string {
  if (typeof path !== 'string' || path.includes('\\') || /[\r\n]/.test(path)) return false
  return /^(?:\/|\/picture\/mine|\/space\/[1-9]\d*)(?:[?#].*)?$/.test(path)
}

export function galleryOrigin(): GalleryOrigin | undefined {
  const origin = window.history.state?.[entryKey]?.origin
  return origin && safeGalleryPath(origin.path) && Number.isSafeInteger(origin.position)
    ? origin
    : undefined
}

export function installGalleryNavigation(router: Router) {
  let previous: Entry | undefined
  router.afterEach((to, from, failure) => {
    if (failure) return
    const state = window.history.state || {}
    const existing: Entry | undefined = state[entryKey]
    let origin = existing?.path === to.fullPath ? existing.origin : undefined
    if (!origin && picturePages.has(String(to.name))) {
      if (galleries.has(String(from.name)) && previous?.path === from.fullPath) {
        origin = { path: previous.path, position: previous.position }
      } else if (picturePages.has(String(from.name))) {
        origin = previous?.origin
      }
    }
    previous = { path: to.fullPath, position: state.position, origin }
    // Merge with Vue Router's state: never overwrite its back/forward/scroll entries.
    window.history.replaceState({ ...state, [entryKey]: previous }, '')
  })
}

export function returnToGallery(router: Router, fallback = '/') {
  const origin = galleryOrigin()
  const position = window.history.state?.position
  if (origin && Number.isSafeInteger(position) && origin.position < position) {
    router.go(origin.position - position)
    return
  }
  return router.push(origin?.path || (safeGalleryPath(fallback) ? fallback : '/'))
}

export function routeViewKey(route: Pick<RouteLocationNormalized, 'name' | 'matched'>) {
  return String(route.name || route.matched[route.matched.length - 1]?.path || 'page')
}
