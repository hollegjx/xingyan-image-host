export const PICTURE_UPLOAD_TIMEOUT_MS = 60_000
export const PICTURE_MAX_SIZE = 2 * 1024 * 1024
export const PICTURE_ALLOWED_SUFFIXES = ['jpg', 'jpeg', 'png', 'gif', 'webp'] as const

export const validatePictureFile = (file: File): string | undefined => {
  const suffix = file.name.split('.').pop()?.toLowerCase()
  if (!suffix || !PICTURE_ALLOWED_SUFFIXES.includes(suffix as (typeof PICTURE_ALLOWED_SUFFIXES)[number])) {
    return '仅支持 JPG、PNG、GIF、WEBP 图片'
  }
  if (file.size > PICTURE_MAX_SIZE) {
    return '图片大小不能超过 2MB'
  }
  return undefined
}
