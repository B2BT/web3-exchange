import { request } from './http'
import type { PageResult } from './chain'

/** 站内通知视图 */
export interface NotificationItem {
  id?: number
  userId?: number
  /** DEPOSIT_CONFIRMED / WITHDRAW_SUCCESS / TRADE_FILLED */
  type?: string
  title?: string
  content?: string
  bizRef?: string
  symbol?: string
  /** 关联金额（最小单位 Long） */
  amount?: number
  /** 0=未读 1=已读 */
  isRead?: number
  createTime?: string
}

/** 分页查询用户通知（create_time 倒序），isRead 可选过滤 0/1 */
export function notifyList(
  userId: number,
  page = 1,
  size = 20,
  isRead?: number,
): Promise<PageResult<NotificationItem>> {
  return request<PageResult<NotificationItem>>({
    url: '/notify/list',
    method: 'get',
    params: { userId, page, size, isRead },
  })
}

/** 统计用户未读通知数 */
export function unreadCount(userId: number): Promise<number> {
  return request<number>({ url: '/notify/unread-count', method: 'get', params: { userId } })
}

/** 标记单条已读 */
export function markRead(id: number, userId: number): Promise<boolean> {
  return request<boolean>({ url: `/notify/${id}/read`, method: 'post', params: { userId } })
}

/** 标记全部已读，返回更新条数 */
export function markAllRead(userId: number): Promise<number> {
  return request<number>({ url: '/notify/read-all', method: 'post', params: { userId } })
}
