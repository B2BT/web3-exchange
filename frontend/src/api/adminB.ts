import { request } from './http'
import type { PageResult } from './chain'

/** 公告 */
export interface Announcement {
  id?: string | number
  title?: string
  content?: string
  type?: number
  status?: number
  publishTime?: string
  publisherId?: string | number
  viewCount?: number
}

/** 审计日志 */
export interface AdminAudit {
  id?: string | number
  adminUserId?: string | number
  adminUsername?: string
  action?: string
  targetType?: string
  targetId?: string
  detail?: string
  ip?: string
  createTime?: string
}

/** 服务健康 */
export interface ServiceHealth {
  id?: string | number
  serviceName?: string
  instanceIp?: string
  port?: number
  status?: number
  memoryUsed?: number
  memoryTotal?: number
  lastHeartbeat?: string
}

/** 交易对 */
export interface AdminSymbol {
  id?: string | number
  symbol?: string
  baseCoin?: string
  quoteCoin?: string
  pricePrecision?: number
  amountPrecision?: number
  priceTick?: number
  minAmount?: number
  maxAmount?: number
  minNotional?: number
  takerFeeRate?: number
  makerFeeRate?: number
  sort?: number
  status?: number
}

// ---- 公告 ----
export function adminAnnouncements(page = 1, size = 20, keyword?: string): Promise<PageResult<Announcement>> {
  return request<PageResult<Announcement>>({ url: '/admin/announcement/list', method: 'get', params: { page, size, keyword } })
}
export function adminAnnouncementCreate(data: Partial<Announcement>): Promise<Announcement> {
  return request<Announcement>({ url: '/admin/announcement/create', method: 'post', data })
}
export function adminAnnouncementUpdate(data: Partial<Announcement>): Promise<Announcement> {
  return request<Announcement>({ url: '/admin/announcement/update', method: 'post', data })
}
export function adminAnnouncementPublish(id: string | number, publish: boolean): Promise<Announcement> {
  return request<Announcement>({ url: `/admin/announcement/${id}/publish`, method: 'post', params: { publish } })
}
export function adminAnnouncementDelete(id: string | number): Promise<void> {
  return request<void>({ url: `/admin/announcement/${id}/delete`, method: 'post' })
}

// ---- 审计 ----
export function adminAudits(page = 1, size = 20): Promise<PageResult<AdminAudit>> {
  return request<PageResult<AdminAudit>>({ url: '/admin/audit/list', method: 'get', params: { page, size } })
}

// ---- 服务健康 ----
export function adminHealth(page = 1, size = 50): Promise<PageResult<ServiceHealth>> {
  return request<PageResult<ServiceHealth>>({ url: '/admin/health/list', method: 'get', params: { page, size } })
}

// ---- 交易对 ----
export function adminSymbols(page = 1, size = 20, keyword?: string): Promise<PageResult<AdminSymbol>> {
  return request<PageResult<AdminSymbol>>({ url: '/admin/symbol/list', method: 'get', params: { page, size, keyword } })
}
export function adminSymbolCreate(data: Partial<AdminSymbol>): Promise<AdminSymbol> {
  return request<AdminSymbol>({ url: '/admin/symbol/create', method: 'post', data })
}
export function adminSymbolUpdate(data: Partial<AdminSymbol>): Promise<AdminSymbol> {
  return request<AdminSymbol>({ url: '/admin/symbol/update', method: 'post', data })
}
export function adminSymbolToggle(id: string | number, trading: boolean): Promise<AdminSymbol> {
  return request<AdminSymbol>({ url: `/admin/symbol/${id}/toggle`, method: 'post', params: { trading } })
}
