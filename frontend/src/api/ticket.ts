import { request } from './http'
import type { PageResult } from './chain'

/** 工单 */
export interface Ticket {
  id?: string | number
  userId?: string | number
  category?: string
  title?: string
  content?: string
  status?: number
  priority?: number
  assigneeId?: string | number
  resolvedAt?: string
  createTime?: string
  updateTime?: string
}

/** 工单回复 */
export interface TicketReply {
  id?: string | number
  ticketId?: string | number
  userId?: string | number
  isStaff?: number
  content?: string
  createTime?: string
}

/** 工单详情 */
export interface TicketDetail extends Ticket {
  replies?: TicketReply[]
}

export const TICKET_CATEGORIES = [
  { label: '充值', value: 'DEPOSIT' },
  { label: '提现', value: 'WITHDRAW' },
  { label: '交易', value: 'TRADE' },
  { label: '账户', value: 'ACCOUNT' },
  { label: '其他', value: 'OTHER' },
]
export const TICKET_STATUS: Record<number, string> = { 0: '待处理', 1: '处理中', 2: '已解决', 3: '已关闭' }

// 用户侧
export function createTicket(data: { category: string; title: string; content: string; priority: number }): Promise<Ticket> {
  return request<Ticket>({ url: '/ticket/create', method: 'post', data })
}
export function myTickets(page = 1, size = 20, status?: number): Promise<PageResult<Ticket>> {
  return request<PageResult<Ticket>>({ url: '/ticket/list', method: 'get', params: { page, size, status } })
}
export function ticketDetail(id: string | number): Promise<TicketDetail> {
  return request<TicketDetail>({ url: `/ticket/${id}`, method: 'get' })
}
export function replyTicket(ticketId: string | number, content: string): Promise<TicketReply> {
  return request<TicketReply>({ url: '/ticket/reply', method: 'post', data: { ticketId, content } })
}
export function closeTicket(id: string | number): Promise<void> {
  return request<void>({ url: `/ticket/${id}/close`, method: 'post' })
}

// 管理侧
export function allTickets(page = 1, size = 20, status?: number): Promise<PageResult<Ticket>> {
  return request<PageResult<Ticket>>({ url: '/admin/ticket/list', method: 'get', params: { page, size, status } })
}
export function adminReplyTicket(ticketId: string | number, content: string): Promise<TicketReply> {
  return request<TicketReply>({ url: '/admin/ticket/reply', method: 'post', data: { ticketId, content } })
}
export function updateTicketStatus(ticketId: string | number, status: number, assigneeId?: string | number): Promise<void> {
  return request<void>({ url: '/admin/ticket/status', method: 'post', data: { ticketId, status, assigneeId } })
}
