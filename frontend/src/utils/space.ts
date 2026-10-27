/** 保留 Long ID 的精度。未知、数组、负数或已经失真的 number 一律拒绝。 */
export function parseId(value: unknown): string | undefined {
  if (typeof value !== 'string' && typeof value !== 'number') return undefined
  if (typeof value === 'number' && !Number.isSafeInteger(value)) return undefined
  const id = String(value)
  if (!/^[1-9]\d{0,18}$/.test(id) || BigInt(id) > 9223372036854775807n) return undefined
  return id
}

export function sameId(left: unknown, right: unknown): boolean {
  const id = parseId(left)
  return !!id && id === parseId(right)
}

export function formatBytes(value?: string | number): string {
  if (value === undefined || value === null) return '—'
  const bytes = Number(value)
  if (!Number.isFinite(bytes) || bytes < 0) return '—'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 ** 2) return `${(bytes / 1024).toFixed(1)} KiB`
  if (bytes < 1024 ** 3) return `${(bytes / 1024 ** 2).toFixed(1)} MiB`
  return `${(bytes / 1024 ** 3).toFixed(2)} GiB`
}

export function usagePercent(used?: string | number, limit?: string | number): number {
  const total = Number(limit)
  if (!Number.isFinite(total) || total <= 0) return 0
  return Math.min(100, Math.max(0, Number(((Number(used || 0) / total) * 100).toFixed(1))))
}

export const spaceLevelName = (level?: number) =>
  ['普通版', '专业版', '旗舰版'][level ?? -1] || '未知等级'

export const spaceTypeName = (type?: number) => (type === 1 ? '团队空间' : '私有空间')
export const roleName = (role?: string) =>
  ({ viewer: '浏览者', editor: '编辑者', admin: '管理员' })[role || ''] || '未知角色'
export const permissions = {
  view: 'picture:view',
  upload: 'picture:upload',
  edit: 'picture:edit',
  delete: 'picture:delete',
  members: 'spaceUser:manage',
  analyze: 'space:analyze',
} as const
export function hasPermission(
  space: { permissionList?: string[] } | undefined,
  permission: string,
): boolean {
  return space?.permissionList?.includes(permission) === true
}
