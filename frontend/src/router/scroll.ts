import type { RouterScrollBehavior } from 'vue-router'
import { routeViewKey } from '@/utils/galleryNavigation'

let generation = 0

// Wait for the entering view, and (when going back) its freshly fetched list.
// No cached private images or permissions are used for restoration.
export const scrollBehavior: RouterScrollBehavior = (to, from, saved) => {
  const ticket = ++generation
  if (!saved && to.path === from.path && to.hash === from.hash) return false
  return new Promise((resolve) => {
    const target = saved || { left: 0, top: 0 }
    const observer = new MutationObserver(check)
    let frame = 0
    const timer = window.setTimeout(() => finish(ticket === generation ? target : false), 12000)
    const interrupt = () => finish(false)
    const events = ['wheel', 'touchstart', 'pointerdown', 'keydown'] as const
    function finish(result: typeof target | false | { el: string; top: number }) {
      observer.disconnect()
      clearTimeout(timer)
      cancelAnimationFrame(frame)
      events.forEach((event) => window.removeEventListener(event, interrupt))
      resolve(result)
    }
    function check() {
      if (ticket !== generation) return finish(false)
      const view = document.querySelector<HTMLElement>('.route-page')
      if (!view || view.dataset.view !== routeViewKey(to)) return
      if (view.className.includes('leave-')) return
      if (
        (saved || to.hash) &&
        (view.className.includes('enter-') || view.querySelector('[aria-busy="true"]'))
      )
        return
      let anchor: HTMLElement | null = null
      try {
        anchor = to.hash ? document.getElementById(decodeURIComponent(to.hash.slice(1))) : null
      } catch {
        /* malformed hashes have no scroll target */
      }
      if (!saved && anchor) finish({ el: to.hash, top: 90 })
      else finish(target)
    }
    observer.observe(document.body, {
      childList: true,
      subtree: true,
      attributes: true,
      attributeFilter: ['class', 'aria-busy', 'data-view'],
    })
    events.forEach((event) =>
      window.addEventListener(event, interrupt, { passive: true, once: true }),
    )
    frame = requestAnimationFrame(check)
  })
}
