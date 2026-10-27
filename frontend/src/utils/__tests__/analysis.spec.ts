import { describe, expect, it } from 'vitest'
import { analysisRequest, chartNumber } from '@/utils/analysis'

const base = {
  scope: 'SPACE' as const,
  spaceId: '9007199254740993',
  timeDimension: 'day' as const,
  startDate: '',
  endDate: '',
  userId: '',
}
describe('图库分析请求与图表数值', () => {
  it('保留 Long 空间与用户 ID', () => {
    expect(analysisRequest({ ...base, userId: '9007199254740995' })).toEqual({
      scope: 'SPACE',
      spaceId: '9007199254740993',
      timeDimension: 'day',
      userId: '9007199254740995',
      topN: 10,
    })
  })
  it('拒绝缺失和失真的空间 ID，不回退公共范围', () => {
    for (const spaceId of [undefined, '0', ['1', '2'], 9007199254740993]) {
      expect(() => analysisRequest({ ...base, spaceId })).toThrow('空间 ID 不合法')
    }
  })
  it('公共与全局请求不附带空间，全局不允许用户筛选', () => {
    expect(analysisRequest({ ...base, scope: 'ALL', userId: '2' })).not.toHaveProperty('userId')
    expect(analysisRequest({ ...base, scope: 'PUBLIC' })).not.toHaveProperty('spaceId')
  })
  it('日期必须成对且顺序正确', () => {
    expect(() => analysisRequest({ ...base, startDate: '2025-01-01' })).toThrow('同时填写')
    expect(() =>
      analysisRequest({ ...base, startDate: '2025-01-02', endDate: '2025-01-01' }),
    ).toThrow('不能晚于')
    expect(
      analysisRequest({ ...base, startDate: '2025-01-01', endDate: '2025-01-02' }).endDate,
    ).toBe('2025-01-02')
  })
  it('拒绝不合法用户 ID', () => {
    expect(() => analysisRequest({ ...base, userId: 'NaN' })).toThrow('用户 ID 不合法')
  })
  it('只把安全非负整数转换为图表值，不做浮点 ID 转换', () => {
    expect(chartNumber('1024')).toBe(1024)
    for (const value of [null, undefined, {}, -1, 'NaN', Infinity, '9007199254740993', 1.5])
      expect(chartNumber(value)).toBe(0)
  })
})
