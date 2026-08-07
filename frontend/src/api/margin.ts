import { request } from './http'
import type { PageResult } from './chain'

/** 杠杆账户视图（金额最小单位 Long） */
export interface MarginAccount {
  id?: string | number
  userId?: string | number
  symbol?: string
  collateral?: number
  borrowed?: number
  interestAccrued?: number
  /** 风险率(百分数) */
  riskRate?: number | null
  status?: number
}

/** 借币记录 */
export interface MarginLoan {
  id?: string | number
  userId?: string | number
  symbol?: string
  requestId?: string
  amount?: number
  rateDaily?: number
  principalRemain?: number
  interestAccrued?: number
  /** 0=借出中 1=已还清 */
  status?: number
  openTime?: string
  repayTime?: string
}

/** 杠杆账户开户 */
export function marginOpen(userId: string | number, symbol: string): Promise<MarginAccount> {
  return request<MarginAccount>({
    url: '/margin/account/open',
    method: 'post',
    params: { userId, symbol },
  })
}

/** 抵押入金（现货→杠杆） */
export function marginTransferIn(userId: string | number, symbol: string, amount: number): Promise<MarginAccount> {
  return request<MarginAccount>({
    url: '/margin/transfer-in',
    method: 'post',
    data: { userId, symbol, amount },
  })
}

/** 抵押出金（杠杆→现货） */
export function marginTransferOut(userId: string | number, symbol: string, amount: number): Promise<MarginAccount> {
  return request<MarginAccount>({
    url: '/margin/transfer-out',
    method: 'post',
    data: { userId, symbol, amount },
  })
}

/** 借币 */
export function marginBorrow(userId: string | number, symbol: string, amount: number): Promise<MarginAccount> {
  return request<MarginAccount>({
    url: '/margin/borrow',
    method: 'post',
    data: { userId, symbol, amount },
  })
}

/** 还币 */
export function marginRepay(userId: string | number, symbol: string, amount: number): Promise<MarginAccount> {
  return request<MarginAccount>({
    url: '/margin/repay',
    method: 'post',
    data: { userId, symbol, amount },
  })
}

/** 杠杆账户详情 */
export function marginAccount(userId: string | number, symbol: string): Promise<MarginAccount> {
  return request<MarginAccount>({
    url: '/margin/account',
    method: 'get',
    params: { userId, symbol },
  })
}

/** 借币记录分页 */
export function marginLoans(userId: string | number, page = 1, size = 20): Promise<PageResult<MarginLoan>> {
  return request<PageResult<MarginLoan>>({
    url: '/margin/loans',
    method: 'get',
    params: { userId, page, size },
  })
}
