import {
  PICTURE_REVIEW_STATUS as REVIEW,
  PICTURE_UPLOAD_STATUS as UPLOAD,
} from '@/constants/picture'

export const submissionFilters = [
  { key: 'all', label: '全部作品' },
  { key: 'published', label: '已公开' },
  { key: 'reviewing', label: '待审核' },
  { key: 'draft', label: '草稿' },
  { key: 'rejected', label: '需修改' },
] as const
export type SubmissionFilter = (typeof submissionFilters)[number]['key']

export function parseSubmissionQuery(query: Record<string, unknown>) {
  const status = submissionFilters.find((item) => item.key === query.status)?.key || 'all'
  const page = typeof query.page === 'string' && /^\d+$/.test(query.page) ? Number(query.page) : 1
  return {
    status,
    search: typeof query.q === 'string' ? query.q.slice(0, 128) : '',
    sort: query.order === 'editTime' ? 'editTime' : 'createTime',
    page: Number.isSafeInteger(page) && page > 0 ? page : 1,
  }
}

export function submissionRequest(
  state: ReturnType<typeof parseSubmissionQuery>,
): API.PictureQueryRequest {
  const request: API.PictureQueryRequest = {
    current: state.page,
    pageSize: 12,
    sortField: state.sort,
    sortOrder: 'desc',
    searchText: state.search.trim() || undefined,
  }
  if (state.status === 'draft') request.uploadStatus = UPLOAD.DRAFT
  else if (state.status !== 'all') {
    // Drafts can carry a review status, but are not submitted works.
    request.uploadStatus = UPLOAD.COMPLETED
    request.reviewStatus = {
      published: REVIEW.PASS,
      reviewing: REVIEW.REVIEWING,
      rejected: REVIEW.REJECT,
    }[state.status]
  }
  return request
}

export function submissionState(picture: API.PictureVO) {
  if (picture.uploadStatus === UPLOAD.DRAFT)
    return { key: 'draft', label: '草稿', hint: '还差一步，完善信息后提交。', action: '继续完善' }
  if (picture.uploadStatus === UPLOAD.COMPLETED) {
    if (picture.reviewStatus === REVIEW.PASS)
      return { key: 'published', label: '已公开', hint: '已展示在公共图库。', action: '编辑作品' }
    if (picture.reviewStatus === REVIEW.REVIEWING)
      return {
        key: 'reviewing',
        label: '待审核',
        hint: '审核通过后，会出现在公共图库。',
        action: '编辑作品',
      }
    if (picture.reviewStatus === REVIEW.REJECT)
      return {
        key: 'rejected',
        label: '需修改',
        hint: picture.reviewMessage || '暂未通过审核，请完善作品信息。',
        action: '修改并提交',
      }
  }
  return { key: 'unknown', label: '状态待确认', hint: '打开详情查看作品信息。', action: '编辑作品' }
}

export function displayDate(value?: string) {
  if (!value) return '日期未提供'
  const date = new Date(value)
  return Number.isNaN(date.getTime())
    ? '日期未提供'
    : date.toLocaleDateString('zh-CN', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
      })
}
