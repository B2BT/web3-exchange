import { request } from './http'
import type { PageData, OrderItem } from './order'

/** 管理后台：分页用户视图 */
export interface AdminUserItem {
  id?: string
  username?: string
  email?: string
  phone?: string
  /** USER 普通 / ADMIN 管理员 */
  role?: string
  /** 1=正常 2=封禁 */
  status?: number
  registerTime?: string
}

/** 管理后台：提现记录视图（金额 = 币种最小单位 Long） */
export interface AdminWithdrawItem {
  id?: number
  requestId?: string
  userId?: string | number
  username?: string
  symbol?: string
  chainCode?: string
  toAddress?: string
  amount?: number
  fee?: number
  /** 0=待审核 1=审核中 2=处理中 3=成功 4=拒绝 5=失败回滚 */
  status?: number
  auditRemark?: string
  failReason?: string
  createTime?: string
}

/** 管理后台：资产汇总视图（金额 = 币种最小单位 Long） */
export interface AdminAssetSummaryItem {
  symbol?: string
  totalAvailable?: number
  totalFrozen?: number
}

/** 用户管理：分页查询 */
export function adminUserList(params: {
  page?: number
  size?: number
  keyword?: string
}): Promise<PageData<AdminUserItem>> {
  return request<PageData<AdminUserItem>>({ url: '/admin/user/list', method: 'get', params })
}

/** 封禁用户（status=2 DISABLED） */
export function adminUserBan(id: string | number): Promise<void> {
  return request<void>({ url: `/admin/user/${id}/ban`, method: 'post' })
}

/** 解封用户（status=1） */
export function adminUserUnban(id: string | number): Promise<void> {
  return request<void>({ url: `/admin/user/${id}/unban`, method: 'post' })
}

/** 全站订单分页（跨用户，OrderVO 同款字段） */
export function adminOrderList(params: {
  page?: number
  size?: number
  symbol?: string
  status?: number
}): Promise<PageData<OrderItem>> {
  return request<PageData<OrderItem>>({ url: '/admin/order/list', method: 'get', params })
}

/** 提现申请分页 */
export function adminWithdrawList(params: {
  page?: number
  size?: number
  status?: number
}): Promise<PageData<AdminWithdrawItem>> {
  return request<PageData<AdminWithdrawItem>>({ url: '/admin/withdraw/list', method: 'get', params })
}

/** 提现审核：通过/拒绝 */
export function adminWithdrawAudit(
  id: string | number,
  data: { approved: boolean; remark?: string },
): Promise<void> {
  return request<void>({ url: `/admin/withdraw/${id}/audit`, method: 'post', data })
}

/** 各币种全站总余额/冻结 */
export function adminAssetSummary(): Promise<AdminAssetSummaryItem[]> {
  return request<AdminAssetSummaryItem[]>({ url: '/admin/asset/summary', method: 'get' })
}
