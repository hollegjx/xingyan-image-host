import axios, { type AxiosRequestConfig } from 'axios'
import { getActivePinia } from 'pinia'
import { message } from 'ant-design-vue'
import { useLoginUserStore } from '@/stores/useLoginUserStore'

type RequestConfig = AxiosRequestConfig & {
  requestType?: string
}

const myAxios = axios.create({
  baseURL: import.meta.env.DEV && import.meta.env.MODE === 'design' ? '' : import.meta.env.VITE_API_BASE_URL || '',
  timeout: 10000,
  withCredentials: true,
})

// 添加请求拦截器
myAxios.interceptors.request.use(function (config) {
  // 在发送请求之前做些什么
  return config;
}, function (error) {
  // 对请求错误做些什么
  return Promise.reject(error);
});

async function handleExpiredSession(code: unknown, url = '') {
  if (code !== 40100 || /\/user\/get\/login(?:[?#]|$)/.test(url) || !getActivePinia()) return
  const store = useLoginUserStore()
  if (!store.loginUser.id) return
  // 同步清空，多个并发请求只触发一次提示和跳转。
  store.clearLoginUser()
  message.warning('登录已过期，请重新登录')
  const { default: router } = await import('@/router')
  const route = router.currentRoute.value
  if (route.path === '/user/login') return
  void router.replace({
    path: '/user/login',
    query: { redirect: route.path === '/space/invite' ? route.path : route.fullPath },
    hash: route.path === '/space/invite' ? route.hash : '',
  })
}

// 添加响应拦截器
myAxios.interceptors.response.use(async function (response) {
  // 2xx 范围内的状态码都会触发该函数。
  // 对响应数据做点什么
  await handleExpiredSession(response.data?.code, response.config.url)
  return response.data;
}, async function (error) {
  await handleExpiredSession(error.response?.data?.code, error.config?.url)
  // 超出 2xx 范围的状态码都会触发该函数。
  // 对响应错误做点什么
  return Promise.reject(error);
});

// 封装请求方法，返回正确的类型
const request = <T>(url: string, config?: RequestConfig): Promise<T> => {
  return myAxios(url, config) as Promise<T>
}

// 导出封装后的请求方法
export default request
