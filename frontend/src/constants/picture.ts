export const PICTURE_REVIEW_STATUS = {
  REVIEWING: 0,
  PASS: 1,
  REJECT: 2,
} as const

export const PICTURE_REVIEW_STATUS_TEXT: Record<number, string> = {
  [PICTURE_REVIEW_STATUS.REVIEWING]: '待审核',
  [PICTURE_REVIEW_STATUS.PASS]: '通过',
  [PICTURE_REVIEW_STATUS.REJECT]: '拒绝',
}

export const PICTURE_REVIEW_STATUS_OPTIONS = Object.entries(PICTURE_REVIEW_STATUS_TEXT).map(
  ([value, label]) => ({ value: Number(value), label }),
)

export const PICTURE_UPLOAD_STATUS = {
  DRAFT: 0,
  COMPLETED: 1,
} as const
