import { describe, expect, it } from 'vitest'
import {
  displayDate,
  parseSubmissionQuery,
  submissionRequest,
  submissionState,
} from '../submissions'

describe('public submissions', () => {
  it.each([
    ['all', undefined, undefined],
    ['draft', 0, undefined],
    ['published', 1, 1],
    ['reviewing', 1, 0],
    ['rejected', 1, 2],
  ])('maps %s to backend statuses without a space or user override', (status, upload, review) => {
    const request = submissionRequest(
      parseSubmissionQuery({ status, userId: 'evil', spaceId: '123', q: '  山  ' }),
    )
    expect(request.uploadStatus).toBe(upload)
    expect(request.reviewStatus).toBe(review)
    expect(request.searchText).toBe('山')
    expect(request).not.toHaveProperty('spaceId')
    expect(request).not.toHaveProperty('userId')
    expect(request.pageSize).toBeLessThanOrEqual(20)
  })
  it('normalizes untrusted URL filters and pagination', () => {
    expect(parseSubmissionQuery({ status: 'invalid', q: ['x'], order: 'sql', page: '-9' })).toEqual(
      { status: 'all', search: '', sort: 'createTime', page: 1 },
    )
    expect(parseSubmissionQuery({ page: '999999999999999999' }).page).toBe(1)
    expect(
      parseSubmissionQuery({ page: '3', order: 'editTime', q: 'x'.repeat(200) }),
    ).toMatchObject({ page: 3, sort: 'editTime', search: 'x'.repeat(128) })
  })
  it('gives draft precedence, never declares missing/unknown status published', () => {
    expect(submissionState({ uploadStatus: 0, reviewStatus: 1 }).key).toBe('draft')
    expect(submissionState({ reviewStatus: 1 }).key).toBe('unknown')
    expect(submissionState({ uploadStatus: 1, reviewStatus: 88 }).key).toBe('unknown')
    expect(
      submissionState({ uploadStatus: 1, reviewStatus: 2, reviewMessage: '来源说明缺失' }).hint,
    ).toBe('来源说明缺失')
  })
  it('does not invent dates', () => {
    expect(displayDate()).toBe('日期未提供')
    expect(displayDate('invalid')).toBe('日期未提供')
    expect(displayDate('2026-10-25T12:00:00+08:00')).toContain('2026')
  })
})
