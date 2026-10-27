import { describe, expect, it } from 'vitest'
import {
  buildPictureSearchFilters,
  recentEditDates,
  type PictureSearchValues,
} from '../pictureSearch'

describe('picture search filters', () => {
  it('normalizes fields without modifying the form and never exports identity/scope', () => {
    const values = {
      searchText: ' 日落 ',
      picFormat: 'PNG',
      tags: ['旅行', '海边', '旅行', ' '],
      category: ' ',
      picWidth: 1920,
      spaceId: 'other',
      userId: 'other',
      current: 99,
    }
    expect(buildPictureSearchFilters(values)).toEqual({
      searchText: '日落',
      picFormat: 'png',
      tags: ['旅行', '海边'],
      picWidth: 1920,
    })
    expect(values.searchText).toBe(' 日落 ')
    expect(values.tags).toHaveLength(4)
  })

  it('includes the whole selected end day with explicit Shanghai boundaries', () => {
    expect(buildPictureSearchFilters({}, ['2026-10-01', '2026-10-01'])).toEqual({
      startEditTime: '2026-09-30T16:00:00.000Z',
      endEditTime: '2026-10-01T16:00:00.000Z',
    })
    expect(buildPictureSearchFilters({}, ['2024-02-29', '2024-02-29']).endEditTime).toBe(
      '2024-02-29T16:00:00.000Z',
    )
  })

  it.each([
    ['2026-10-02', '2026-10-01'],
    ['2026-02-30', '2026-03-01'],
    ['invalid', '2026-10-01'],
    ['2026-10-01'],
    ['1969-12-01', '1970-01-01'],
  ])('rejects invalid calendar ranges %s', (...range) => {
    expect(() => buildPictureSearchFilters({}, range)).toThrow()
  })

  it.each([0, -1, 1.5, 2147483648, Number.NaN])('rejects invalid dimensions %s', (value) => {
    expect(() => buildPictureSearchFilters({ picWidth: value })).toThrow()
    expect(() => buildPictureSearchFilters({ picHeight: value })).toThrow()
  })

  it('cleared input controls do not send zero dimensions or stale dates', () => {
    expect(
      buildPictureSearchFilters(
        { picWidth: null, picHeight: undefined } as unknown as PictureSearchValues,
        null,
      ),
    ).toEqual({})
    expect(buildPictureSearchFilters({}, [])).toEqual({})
  })

  it('recent dates count calendar days in Shanghai, independently of browser timezone', () => {
    expect(recentEditDates(7, new Date('2026-10-21T17:00:00Z'))).toEqual([
      '2026-10-16',
      '2026-10-22',
    ])
    expect(recentEditDates(30, new Date('2026-10-21T17:00:00Z'))).toEqual([
      '2026-09-23',
      '2026-10-22',
    ])
  })
})
