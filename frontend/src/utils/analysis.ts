import { parseId } from '@/utils/space'

/** 只把统计值转换为图表 number，ID 始终保留字符串。超出 JS 精度时拒绝作图。 */
export function chartNumber(value: unknown): number {
  if (typeof value !== 'number' && typeof value !== 'string') return 0
  const number = Number(value)
  return Number.isSafeInteger(number) && number >= 0 ? number : 0
}

export function analysisRequest(options: {
  scope: 'SPACE' | 'PUBLIC' | 'ALL'
  spaceId?: unknown
  timeDimension: 'day' | 'week' | 'month'
  startDate: string
  endDate: string
  userId: string
}): API.SpaceAnalyzeRequest {
  const request: API.SpaceAnalyzeRequest = {
    scope: options.scope,
    timeDimension: options.timeDimension,
    topN: 10,
  }
  if (options.scope === 'SPACE') {
    const id = parseId(options.spaceId)
    if (!id) throw new Error('空间 ID 不合法')
    request.spaceId = id
  }
  if (!!options.startDate !== !!options.endDate) throw new Error('请同时填写开始和结束日期')
  if (options.startDate) {
    if (options.startDate > options.endDate) throw new Error('开始日期不能晚于结束日期')
    request.startDate = options.startDate
    request.endDate = options.endDate
  }
  if (options.userId && options.scope !== 'ALL') {
    const id = parseId(options.userId)
    if (!id) throw new Error('用户 ID 不合法')
    request.userId = id
  }
  return request
}
