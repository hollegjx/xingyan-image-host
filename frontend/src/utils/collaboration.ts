export interface EditState {
  roomId: string
  sourceVersion: string
  revision: string
  editorId?: string | null
  editorName?: string | null
  leaseToken?: string | null
  rotation: number
  zoom: number
  editing: boolean
  saving: boolean
}

export function readEditState(value: unknown): EditState {
  const s = value as Partial<EditState> | null
  if (
    !s ||
    typeof s.roomId !== 'string' ||
    typeof s.sourceVersion !== 'string' ||
    typeof s.revision !== 'string' ||
    !/^\d+$/.test(s.revision) ||
    !Number.isInteger(s.rotation) ||
    s.rotation! < 0 ||
    s.rotation! > 3 ||
    !Number.isInteger(s.zoom) ||
    Math.abs(s.zoom!) > 8 ||
    typeof s.editing !== 'boolean' ||
    typeof s.saving !== 'boolean' ||
    (s.editing && typeof s.leaseToken !== 'string')
  )
    throw new Error('协同状态无效')
  return s as EditState
}

export function collaborationUrl(
  id: string,
  base = import.meta.env.VITE_API_BASE_URL || '',
  origin = window.location.origin,
) {
  if (!/^[1-9]\d{0,18}$/.test(id)) throw new Error('图片 ID 无效')
  const url = new URL(`${base}/api/ws/picture/edit`, origin)
  if (url.origin !== origin) throw new Error('协同编辑仅支持同源连接')
  url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:'
  url.searchParams.set('pictureId', id)
  return url.toString()
}

/** Quarter turns swap output dimensions; zoom is centered, with transparent padding when shrinking. */
export function editDimensions(width: number, height: number, rotation: number) {
  return rotation % 2 ? { width: height, height: width } : { width, height }
}

export function drawEdit(canvas: HTMLCanvasElement, image: HTMLImageElement, state: EditState) {
  const { width, height } = editDimensions(image.naturalWidth, image.naturalHeight, state.rotation)
  if (!width || !height || width * height > 16_000_000)
    throw new Error('仅支持不超过 1600 万像素的图片')
  canvas.width = width
  canvas.height = height
  const context = canvas.getContext('2d')
  if (!context) throw new Error('浏览器不支持图片编辑')
  context.save()
  context.translate(width / 2, height / 2)
  context.rotate((state.rotation * Math.PI) / 2)
  const scale = 1.1 ** state.zoom
  context.scale(scale, scale)
  context.drawImage(image, -image.naturalWidth / 2, -image.naturalHeight / 2)
  context.restore()
}
