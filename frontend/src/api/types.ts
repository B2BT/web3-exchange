/** 后端统一响应结构 Result<T> */
export interface ApiResult<T = unknown> {
  code: number
  message: string
  data: T
  timestamp?: number
  requestId?: string
  success?: boolean
  error?: boolean
}

/** 验证码响应 */
export interface CaptchaData {
  captchaId: string
  /** 生产为 base64 图片(data:image/...)，测试环境可能是算式文本(如 "1-5") */
  captchaImage: string
  captchaText?: string
  type?: string
  expireSeconds?: number
  timestamp?: number
}

/** 用户信息（登录响应内嵌） */
export interface UserInfo {
  id: number
  username: string
  realName?: string
  nickname?: string
  email?: string
  phone?: string
  avatar?: string
  gender?: number
  status?: number
  roles?: string[]
  permissions?: string[]
  authorities?: string[]
}

/** 登录响应 */
export interface LoginData {
  accessToken: string
  refreshToken?: string
  tokenType?: string
  expiresIn?: number
  refreshExpiresIn?: number
  userInfo: UserInfo
  authorities?: string[]
  roles?: string[]
  needChangePassword?: boolean
  firstLogin?: boolean
  sessionId?: string
}

/** 登录请求 */
export interface LoginRequest {
  username: string
  password: string
  captcha?: string
  captchaId?: string
  totpCode?: string
  device?: string
  rememberMe?: boolean
}

/** 注册请求 */
export interface RegisterRequest {
  username: string
  password: string
  email: string
  phone: string
  nickname?: string
  inviteCode?: string
  captcha?: string
  captchaId?: string
  source?: string
}
