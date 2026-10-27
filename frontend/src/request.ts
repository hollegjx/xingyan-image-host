import axios, { type AxiosRequestConfig } from 'axios'

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

// 添加响应拦截器
myAxios.interceptors.response.use(function (response) {
  // 2xx 范围内的状态码都会触发该函数。
  // 对响应数据做点什么
  return response.data;
}, function (error) {
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
