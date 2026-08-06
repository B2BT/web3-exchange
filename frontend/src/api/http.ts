import axios, {
  type AxiosInstance,
  type AxiosResponse,
  type InternalAxiosRequestConfig,
} from 'axios'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import type { ApiResult } from './types'

/**
 * 统一 Axios 实例
 * baseURL=/api，经 Vite dev server proxy 转发到网关 8080
 */
const service: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 20000,
})

// 请求拦截器：附加 Bearer token
service.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const authStore = useAuthStore()
    if (authStore.accessToken) {
      config.headers.Authorization = `Bearer ${authStore.accessToken}`
    }
    return config
  },
  (error) => Promise.reject(error),
)

// 响应拦截器：统一处理 ApiResult 与 401
service.interceptors.response.use(
  (response: AxiosResponse<ApiResult>): any => {
    const res = response.data
    // 兼容非 Result 包装（极少见），直接透传
    if (res === undefined || res === null || typeof res !== 'object' || res.code === undefined) {
      return res
    }
    if (res.code === 200) {
      return res.data
    }
    // 业务失败
    ElMessage.error(res.message || '请求失败')
    return Promise.reject(new Error(res.message || '请求失败'))
  },
  (error) => {
    const status = error?.response?.status
    const data = error?.response?.data
    // 401 未认证：清除 token 并跳转登录
    if (status === 401) {
      const authStore = useAuthStore()
      authStore.clearAuth()
      if (!window.location.pathname.includes('/login')) {
        ElMessage.error('登录已过期，请重新登录')
        window.location.href = '/login'
      }
    } else if (status === 403) {
      ElMessage.error('没有权限执行该操作')
    } else if (data?.message) {
      ElMessage.error(data.message)
    } else {
      ElMessage.error(error?.message || '网络异常，请稍后重试')
    }
    return Promise.reject(error)
  },
)

/** 泛型请求封装：返回 data 部分 */
export function request<T>(config: Parameters<AxiosInstance['request']>[0]): Promise<T> {
  return service.request(config) as unknown as Promise<T>
}

export default service
