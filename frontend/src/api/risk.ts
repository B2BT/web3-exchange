import { request } from './http'
import type { PageResult } from './chain'

/** 风控规则 */
export interface RiskRule {
  id?: string | number
  ruleCode?: string
  name?: string
  ruleType?: string
  scope?: string
  symbol?: string | null
  threshold?: number
  status?: number
}

/** 反钓鱼码 */
export interface AntiPhishing {
  id?: string | number
  userId?: string | number
  phrase?: string
}

/** 登录日志 */
export interface LoginLog {
  id?: string | number
  userId?: string | number
  username?: string
  ip?: string
  device?: string
  /** 0=成功 1=失败 */
  result?: number
  /** 0=正常 1=异常 */
  risk?: number
  createTime?: string
}

/** 风控规则列表 */
export function riskRules(): Promise<RiskRule[]> {
  return request<RiskRule[]>({ url: '/risk/rules', method: 'get' })
}

/** 设置反钓鱼码 */
export function riskSetPhishing(userId: string | number, phrase: string): Promise<AntiPhishing> {
  return request<AntiPhishing>({ url: '/risk/phishing/set', method: 'post', data: { userId, phrase } })
}

/** 查询反钓鱼码 */
export function riskGetPhishing(userId: string | number): Promise<AntiPhishing> {
  return request<AntiPhishing>({ url: '/risk/phishing', method: 'get', params: { userId } })
}

/** 我的登录日志分页 */
export function riskLoginLogs(userId: string | number, page = 1, size = 20): Promise<PageResult<LoginLog>> {
  return request<PageResult<LoginLog>>({ url: '/risk/login-logs', method: 'get', params: { userId, page, size } })
}
