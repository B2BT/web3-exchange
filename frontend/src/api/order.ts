import { request } from './http'

/** 下单请求（金额一律 Long 最小单位） */
export interface PlaceOrderRequest {
  userId: number
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
  /** 客户端幂等号 */
  clientOid?: string
}

/** 订单视图 */
export interface OrderItem {
  id?: number
  orderNo?: string
  clientOid?: string
  userId?: number
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

/** 下单 */
export function placeOrder(data: PlaceOrderRequest): Promise<PlaceOrderResult> {
  return request<PlaceOrderResult>({ url: '/order/place', method: 'post', data })
}

/** 查询订单 */
export function getOrder(userId: number, orderNo: string): Promise<OrderItem> {
  return request<OrderItem>({ url: '/order/get', method: 'get', params: { userId, orderNo } })
}

/** 查询订单成交明细 */
export function getTrades(userId: number, orderNo: string): Promise<TradeItem[]> {
  return request<TradeItem[]>({ url: '/order/trades', method: 'get', params: { userId, orderNo } })
}
