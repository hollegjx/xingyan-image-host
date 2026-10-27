import { defineStore } from 'pinia'
import { ref, watch } from 'vue'
import { useLoginUserStore } from './useLoginUserStore'
import { parseId } from '@/utils/space'

// Only UI choices and navigation labels. Never a source of access permissions or image data.
export const useNavigationStore = defineStore('galleryNavigation', () => {
  const user = useLoginUserStore()
  const spaceTypes = ref<Record<string, number>>({})
  const pictureSpaces = ref<Record<string, string | null>>({})
  const listViews = new Map<string, unknown>()
  watch(
    () => user.loginUser.id,
    () => {
      spaceTypes.value = {}
      pictureSpaces.value = {}
      listViews.clear()
    },
    { flush: 'sync' },
  )
  function rememberSpace(space: API.SpaceVO) {
    const id = parseId(space.id)
    if (id && (space.spaceType === 0 || space.spaceType === 1)) {
      if (Object.keys(spaceTypes.value).length >= 100) spaceTypes.value = {}
      spaceTypes.value[id] = space.spaceType
    }
  }
  function rememberPicture(picture: API.PictureVO) {
    const id = parseId(picture.id)
    if (id) {
      if (Object.keys(pictureSpaces.value).length >= 100) pictureSpaces.value = {}
      pictureSpaces.value[id] = parseId(picture.spaceId) || null
    }
  }
  function rememberList(key: string, value: unknown) {
    if (listViews.size >= 20 && !listViews.has(key))
      listViews.delete(listViews.keys().next().value!)
    listViews.set(key, JSON.parse(JSON.stringify(value)))
  }
  function readList<T>(key: string): T | undefined {
    const value = listViews.get(key)
    return value ? (JSON.parse(JSON.stringify(value)) as T) : undefined
  }
  return { spaceTypes, pictureSpaces, rememberSpace, rememberPicture, rememberList, readList }
})
