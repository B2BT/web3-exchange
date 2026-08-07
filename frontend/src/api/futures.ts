import { request } from './http'

/** 合约交易对 */
export interface SwapContract {
  id?: string | number
  symbol?: string
  base?: string
  quote?: string
  maxLeverage?: number
  mmr?: number
  imr?: number
  status?: number
}

/** 合约订单 */
export interface FuturesOrder {
  id?: string | number
  orderNo?: string
  userId?: string | number
  symbol?: string
  side?: number // 1开多 2开空 3平多 4平空
  orderType?: number
  price?: string | number
  quantity?: string | number
  filled?: string | number
  remaining?: string | number
  avgPrice?: string | number
  leverage?: number
  status?: number
}

/** 合约账户 */
export interface FuturesAccount {
  userId?: string | number
  coin?: string
  marginBalance?: string | number
  availableBalance?: string | number
  positionMargin?: string | number
  unrealizedPnl?: string | number
  realizedPnl?: string | number
}

/** 合约持仓 */
export interface FuturesPosition {
  id?: string | number
  userId?: string | number
  symbol?: string
  side?: number // 1多 2空
  size?: string | number
  entryPrice?: string | number
  leverage?: number
  isolatedMargin?: string | number
  liqPrice?: string | number
  unrealizedPnl?: string | number
  realizedPnl?: string | number
  status?: number
}

export function contracts(): Promise<SwapContract[]> {
  return request<SwapContract[]>({ url: '/futures/contracts', method: 'get' })
}

export function markPrice(symbol: string): Promise<string | number> {
  return request<string | number>({ url: `/futures/mark/${symbol}`, method: 'get' })
}

export function account(userId: string | number, coin = 'USDT'): Promise<FuturesAccount> {
  return request<FuturesAccount>({ url: '/futures/account', method: 'get', params: { userId, coin } })
}

export function positions(userId: string | number): Promise<FuturesPosition[]> {
  return request<FuturesPosition[]>({ url: '/futures/position', method: 'get', params: { userId } })
}

export interface PlaceFuturesOrder {
  userId?: string | number
  symbol?: string
  side?: number
  orderType?: number
  price?: string
  quantity?: string
  leverage?: number
  marginMode?: number
}

export function placeOrder(data: PlaceFuturesOrder): Promise<FuturesOrder> {
  return request<FuturesOrder>({ url: '/futures/order', method: 'post', data })
}

export function futuresDeposit(userId: string | number, amount: string, coin = 'USDT'): Promise<FuturesAccount> {
  return request<FuturesAccount>({ url: '/futures/deposit', method: 'post', params: { userId, coin, amount } })
}
