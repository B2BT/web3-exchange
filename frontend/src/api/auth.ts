import { request } from './http'
import type {
  CaptchaData,
  LoginData,
  LoginRequest,
  RegisterRequest,
} from './types'

/** 获取图形验证码 (GET) */
export function getCaptcha(): Promise<CaptchaData> {
  return request<CaptchaData>({ url: '/auth/captcha', method: 'get' })
}

/** 用户注册 */
export function register(data: RegisterRequest): Promise<void> {
  return request<void>({ url: '/auth/register', method: 'post', data })
}

/** 用户登录 */
export function login(data: LoginRequest): Promise<LoginData> {
  return request<LoginData>({ url: '/auth/login', method: 'post', data })
}

/** 登出 */
export function logout(refreshToken?: string): Promise<void> {
  return request<void>({
    url: '/auth/logout',
    method: 'post',
    data: refreshToken ? { refreshToken } : {},
  })
}

/** 验证令牌 */
export function validateToken(): Promise<boolean> {
  return request<boolean>({ url: '/auth/validate', method: 'post' })
}
