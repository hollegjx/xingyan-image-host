// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** category POST /api/space/analyze/category */
export async function categoryUsingPost(
  body: API.SpaceAnalyzeRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseListDistribution_>('/api/space/analyze/category', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** dashboard POST /api/space/analyze/dashboard */
export async function dashboardUsingPost(
  body: API.SpaceAnalyzeRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseSpaceAnalysisVO_>('/api/space/analyze/dashboard', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** rank POST /api/space/analyze/rank */
export async function rankUsingPost(
  body: API.SpaceAnalyzeRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseListRanking_>('/api/space/analyze/rank', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** size POST /api/space/analyze/size */
export async function sizeUsingPost(
  body: API.SpaceAnalyzeRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseListDistribution_>('/api/space/analyze/size', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** tag POST /api/space/analyze/tag */
export async function tagUsingPost(
  body: API.SpaceAnalyzeRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseListDistribution_>('/api/space/analyze/tag', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** usage POST /api/space/analyze/usage */
export async function usageUsingPost(
  body: API.SpaceAnalyzeRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseUsage_>('/api/space/analyze/usage', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** user POST /api/space/analyze/user */
export async function userUsingPost(
  body: API.SpaceAnalyzeRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseTrend_>('/api/space/analyze/user', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}
