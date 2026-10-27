// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** source GET /api/picture/editing/source */
export async function sourceUsingGet(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.sourceUsingGETParams,
  options?: { [key: string]: any }
) {
  return request<any>('/api/picture/editing/source', {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  })
}

/** apply POST /api/picture/out_painting/apply */
export async function applyUsingPost(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.applyUsingPOSTParams,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponsePictureVO_>('/api/picture/out_painting/apply', {
    method: 'POST',
    params: {
      ...params,
    },
    ...(options || {}),
  })
}

/** capabilities GET /api/picture/out_painting/capabilities */
export async function capabilitiesUsingGet(options?: { [key: string]: any }) {
  return request<API.BaseResponseMapStringBoolean_>('/api/picture/out_painting/capabilities', {
    method: 'GET',
    ...(options || {}),
  })
}

/** create POST /api/picture/out_painting/create_task */
export async function createUsingPost(
  body: API.AiOutpaintingRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseAiTaskVO_>('/api/picture/out_painting/create_task', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** get GET /api/picture/out_painting/get_task */
export async function getUsingGet(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.getUsingGETParams,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseAiTaskVO_>('/api/picture/out_painting/get_task', {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  })
}

/** latest GET /api/picture/out_painting/latest */
export async function latestUsingGet(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.latestUsingGETParams,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseAiTaskVO_>('/api/picture/out_painting/latest', {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  })
}

/** result GET /api/picture/out_painting/result */
export async function resultUsingGet(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.resultUsingGETParams,
  options?: { [key: string]: any }
) {
  return request<any>('/api/picture/out_painting/result', {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  })
}
