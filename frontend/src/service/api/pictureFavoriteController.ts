import request from '@/request'
type Result<T> = { code?: number; message?: string; data?: T }
export const setPictureFavorite = (pictureId: string | number, favorite: boolean) =>
  request<Result<{ favoriteCount: string | number; favorited: boolean }>>('/api/picture/favorite/set', {
    method: 'POST',
    data: { pictureId, favorite },
  })
export const listMyFavorites = (current: number, pageSize = 12) =>
  request<Result<{ records?: API.PictureVO[]; total?: string | number }>>('/api/picture/favorite/mine', {
    method: 'POST',
    data: { current, pageSize },
  })

export type FavoriteStatus = { favoriteCount: string | number; favorited?: boolean }
export const getPictureFavoriteStatuses = (pictureIds: (string | number)[]) =>
  request<Result<Record<string, FavoriteStatus>>>('/api/picture/favorite/status', {
    method: 'POST', data: { pictureIds },
  })
