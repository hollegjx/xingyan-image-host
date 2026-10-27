/** Only editable filters; identity, space, visibility and pagination belong to the parent/server. */
export type PictureSearchFilters = Pick<
  API.PictureQueryRequest,
  | 'searchText'
  | 'category'
  | 'tags'
  | 'name'
  | 'introduction'
  | 'picWidth'
  | 'picHeight'
  | 'picFormat'
  | 'startEditTime'
  | 'endEditTime'
>

export type PictureSearchValues = Omit<PictureSearchFilters, 'startEditTime' | 'endEditTime'>

const DAY_MS = 86400000
const SHANGHAI_OFFSET_MS = 8 * 3600000

function calendarDay(date: Date): string {
  return new Date(date.getTime() + SHANGHAI_OFFSET_MS).toISOString().slice(0, 10)
}

function midnight(day: string): Date {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(day) || Number(day.slice(0, 4)) < 1970)
    throw new Error('请选择 1970 年及以后的有效日期')
  const value = new Date(`${day}T00:00:00.000+08:00`)
  if (!Number.isFinite(value.getTime()) || calendarDay(value) !== day)
    throw new Error('编辑日期无效')
  return value
}

/** Calendar days are inclusive in the UI, but the API uses [start, next-day end), UTC+08:00. */
export function buildPictureSearchFilters(
  values: PictureSearchValues,
  dateRange?: string[] | null,
): PictureSearchFilters {
  const result: PictureSearchFilters = {}
  for (const key of ['searchText', 'category', 'name', 'introduction', 'picFormat'] as const) {
    const value = values[key]?.trim()
    if (value) result[key] = key === 'picFormat' ? value.toLowerCase() : value
  }
  const tags = [...new Set((values.tags || []).map((tag) => tag.trim()).filter(Boolean))]
  if (tags.length) result.tags = tags.sort()
  for (const key of ['picWidth', 'picHeight'] as const) {
    const value = values[key]
    if (value !== undefined && value !== null) {
      if (!Number.isInteger(value) || value <= 0 || value > 2147483647)
        throw new Error('图片宽度和高度必须是有效的正整数')
      result[key] = value
    }
  }
  if (dateRange?.length) {
    if (dateRange.length !== 2 || !dateRange[0] || !dateRange[1])
      throw new Error('请选择完整的编辑日期范围')
    const start = midnight(dateRange[0])
    const end = midnight(dateRange[1])
    if (start > end) throw new Error('开始日期不能晚于结束日期')
    if (dateRange[1] === '9999-12-31') throw new Error('结束日期必须早于 9999-12-31')
    result.startEditTime = start.toISOString()
    result.endEditTime = new Date(end.getTime() + DAY_MS).toISOString()
  }
  return result
}

export function recentEditDates(days: number, now = new Date()): [string, string] {
  return [calendarDay(new Date(now.getTime() - (days - 1) * DAY_MS)), calendarDay(now)]
}
