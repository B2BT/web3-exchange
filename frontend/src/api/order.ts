import { request } from './http'

/** 下单请求（金额一律 Long 最小单位） */
export interface PlaceOrderRequest {
  userId: string | number
  symbol: string
  /** 方向：1=BUY 2=SELL */
  side: number
  /** 类型：1=限价 2=市价 */
  orderType: number
  /** 限价（计价币最小单位；市价为 0） */
  price?: number | null
  /** 数量（基础币最小单位；市价买单为 0） */
  quantity?: number | null
  /** 市价买单预算额（计价币最小单位） */
  quoteAmount?: number | null
  /** 时间策略：0=GTC(长期有效) 1=IOC(立即成交或取消剩余) 2=FOK(全部成交否则取消) 3=PostOnly(只挂单不吃单) */
  timeInForce?: number
  /** 客户端幂等号 */
  clientOid?: string
}

/** 订单视图 */
export interface OrderItem {
  id?: number
  orderNo?: string
  clientOid?: string
  userId?: string | number
  symbol?: string
  baseCoin?: string
  quoteCoin?: string
  side?: number
  orderType?: number
  price?: number | null
  quantity?: number | null
  quoteAmount?: number | null
  remaining?: number | null
  filledAmount?: number | null
  filledQuoteAmount?: number | null
  avgPrice?: number | null
  status?: number
  remark?: string
  createTime?: string
}

/** 成交视图 */
export interface TradeItem {
  id?: number
  tradeNo?: string
  symbol?: string
  price?: number | null
  quantity?: number | null
  quoteAmount?: number | null
  takerSide?: number
  settleStatus?: number
  tradeTime?: string
}

/** 下单结果（订单 + 本次成交） */
export interface PlaceOrderResult {
  order?: OrderItem
  trades?: TradeItem[]
}

/** 深度盘口单档（price/quantity 均为 Long 最小单位） */
export interface DepthLevel {
  price?: number | null
  quantity?: number | null
}

/** 深度盘口视图 */
export interface DepthItem {
  symbol?: string
  /** 买盘（价格降序，最高价在前） */
  bids?: DepthLevel[]
  /** 卖盘（价格升序，最低价在前） */
  asks?: DepthLevel[]
}

/** 最近成交视图（按 tradeTime 降序，最新在前） */
export interface RecentTradeItem {
  id?: number | string
  tradeNo?: string
  symbol?: string
  /** 价格（计价币最小单位 Long） */
  price?: number | null
  /** 数量（基础币最小单位 Long） */
  quantity?: number | null
  /** 成交额（计价币最小单位 Long） */
  quoteAmount?: number | null
  /** 方向：1=主动买 2=主动卖（后端字段 takerSide） */
  takerSide?: number
  tradeTime?: string
}

/** 下单 */
export function placeOrder(data: PlaceOrderRequest): Promise<PlaceOrderResult> {
  return request<PlaceOrderResult>({ url: '/order/place', method: 'post', data })
}

/** 查询订单 */
export function getOrder(userId: string | number, orderNo: string): Promise<OrderItem> {
  return request<OrderItem>({ url: '/order/get', method: 'get', params: { userId, orderNo } })
}

/** 查询订单成交明细 */
export function getTrades(userId: string | number, orderNo: string): Promise<TradeItem[]> {
  return request<TradeItem[]>({ url: '/order/trades', method: 'get', params: { userId, orderNo } })
}

/** 深度盘口（公开只读行情，带 symbol + limit） */
export function depth(params: { symbol: string; limit?: number }): Promise<DepthItem> {
  return request<DepthItem>({ url: '/order/depth', method: 'get', params })
}

/** 最近成交（公开只读行情，按 tradeTime 降序取前 limit） */
export function recentTrades(params: {
  symbol: string
  limit?: number
}): Promise<RecentTradeItem[]> {
  return request<RecentTradeItem[]>({ url: '/order/recent-trades', method: 'get', params })
}
