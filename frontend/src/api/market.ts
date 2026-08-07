import { request } from './http'

/** Ticker 视图（金额 Long 最小单位） */
export interface TickerItem {
  symbol?: string
  /** 最新成交价（计价币最小单位） */
  lastPrice?: number | null
  /** 24h 涨跌幅（基点 bp，10000=100%） */
  change24h?: number | null
  high24h?: number | null
  low24h?: number | null
  /** 24h 成交量（基础币最小单位） */
  volume24h?: number | null
  /** 24h 成交额（计价币最小单位） */
  quoteVolume24h?: number | null
}

/** K线视图（金额 Long 最小单位） */
export interface KlineItem {
  symbol?: string
  interval?: string
  /** 窗口开始时间（epoch millis） */
  openTime?: number | null
  open?: number | null
  high?: number | null
  low?: number | null
  close?: number | null
  /** 成交量（基础币最小单位） */
  volume?: number | null
  /** 成交额（计价币最小单位） */
  quoteVolume?: number | null
}

/** 全市场 ticker 列表 */
export function tickerList(): Promise<TickerItem[]> {
  return request<TickerItem[]>({ url: '/market/ticker/list', method: 'get' })
}

/**
 * K线列表。
 * 注意 symbol 含斜杠（如 BTC/USDT），query 参数需手动 encodeURIComponent，
 * 避免被路径/参数序列化二次编码导致服务端解不出原始交易对。
 */
export function kline(symbol: string, period: string, limit = 500): Promise<KlineItem[]> {
  return request<KlineItem[]>({
    url: `/market/kline/list?symbol=${encodeURIComponent(symbol)}`,
    method: 'get',
    params: { period, limit },
  })
}

/** 深度档位（价格/数量，Long 最小单位） */
export interface DepthLevel {
  price: number
  quantity: number
}
/** 深度盘口（用于深度图） */
export interface DepthData {
  symbol?: string
  bids?: DepthLevel[]
  asks?: DepthLevel[]
}

/** 订单簿深度（存于 order 域，市场数据公开） */
export function depth(symbol: string, limit = 20): Promise<DepthData> {
  return request<DepthData>({
    url: `/order/depth?symbol=${encodeURIComponent(symbol)}`,
    method: 'get',
    params: { limit },
  })
}

