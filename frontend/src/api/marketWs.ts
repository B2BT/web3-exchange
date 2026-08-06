import type { KlineItem, TickerItem } from './market'

/**
 * 实时行情 WebSocket（market 域，MVP：ticker + kline）
 * 契约见 docs/ws-realtime.md
 *
 * - 端点：优先经网关 ws://127.0.0.1:8080/api/market/ws，连接失败自动回退直连
 *   ws://127.0.0.1:8106/ws
 * - 断线自动重连（指数退避 1s/2s/4s… 封顶 10s），重连成功后自动恢复全部订阅
 * - 心跳：每 20s 发 { op: 'ping' }，服务端回 pong
 * - 全站单例复用一条连接；页面 mount 时 connect()+subscribe，卸载时 close()
 */

const PRIMARY_URL = 'ws://127.0.0.1:8080/api/market/ws'
const FALLBACK_URL = 'ws://127.0.0.1:8106/ws'

export type MarketWsChannel = 'ticker' | 'kline'

export interface MarketWsCallbacks {
  /** ticker 推送：symbol, data（TickerVO，Long 最小单位） */
  onTicker?: (symbol: string, data: TickerItem) => void
  /** kline 推送：symbol, period, data（KlineVO，Long 最小单位） */
  onKline?: (symbol: string, period: string, data: KlineItem) => void
  /** 连接状态变化 */
  onStatus?: (connected: boolean) => void
}

const MAX_RETRY = 10000 // 重连退避封顶 10s
const HEARTBEAT_MS = 20000

function subKey(channel: MarketWsChannel, symbol: string, period?: string): string {
  return period ? `${channel}:${symbol}:${period}` : `${channel}:${symbol}`
}

type Sub = { channel: MarketWsChannel; symbol: string; period?: string }

class MarketWs {
  private ws: WebSocket | null = null
  private callbacks: MarketWsCallbacks = {}
  private subs = new Map<string, Sub>()
  private retry = 1000
  private retryTimer: number | null = null
  private heartbeatTimer: number | null = null
  private closedByUser = false
  private urlIndex = 0
  private urlConnected = false
  private urls = [PRIMARY_URL, FALLBACK_URL]

  /** 注册回调（覆盖式，页面 mount 时调用） */
  on(cb: MarketWsCallbacks): this {
    this.callbacks = { ...cb }
    return this
  }

  /** 建立连接（幂等：已连接或连接中则忽略） */
  connect(): this {
    this.closedByUser = false
    if (this.ws) return this
    this.open()
    return this
  }

  /** 订阅（重复订阅幂等；未连接时先登记，连接建立后自动补发） */
  subscribe(channel: MarketWsChannel, symbol: string, period?: string): this {
    this.subs.set(subKey(channel, symbol, period), { channel, symbol, period })
    this.send({ op: 'subscribe', channel, symbol, ...(period ? { period } : {}) })
    return this
  }

  /** 取消订阅 */
  unsubscribe(channel: MarketWsChannel, symbol: string, period?: string): this {
    this.subs.delete(subKey(channel, symbol, period))
    this.send({ op: 'unsubscribe', channel, symbol, ...(period ? { period } : {}) })
    return this
  }

  /** 关闭连接并清空订阅（页面卸载时调用） */
  close(): this {
    this.closedByUser = true
    if (this.retryTimer != null) {
      clearTimeout(this.retryTimer)
      this.retryTimer = null
    }
    this.stopHeartbeat()
    this.subs.clear()
    const w = this.ws
    this.ws = null
    if (w) {
      try {
        w.close()
      } catch {
        /* 忽略关闭异常 */
      }
    }
    return this
  }

  private open(): void {
    const url = this.urls[this.urlIndex]
    let ws: WebSocket
    try {
      ws = new WebSocket(url)
    } catch {
      this.scheduleReconnect()
      return
    }
    this.ws = ws
    this.urlConnected = false

    ws.onopen = () => {
      this.urlConnected = true
      this.retry = 1000
      this.callbacks.onStatus?.(true)
      this.resubscribeAll()
      this.startHeartbeat()
    }

    ws.onmessage = (ev) => this.dispatch(ev.data)

    ws.onerror = () => {
      // 从未建立连接（open 失败）→ 切换备用地址再试
      if (!this.urlConnected) this.urlIndex = (this.urlIndex + 1) % this.urls.length
    }

    ws.onclose = () => {
      if (this.ws === ws) this.ws = null
      this.stopHeartbeat()
      this.callbacks.onStatus?.(false)
      this.scheduleReconnect()
    }
  }

  private send(obj: unknown): void {
    const w = this.ws
    if (w && w.readyState === WebSocket.OPEN) {
      w.send(JSON.stringify(obj))
    }
  }

  private resubscribeAll(): void {
    for (const { channel, symbol, period } of this.subs.values()) {
      this.send({ op: 'subscribe', channel, symbol, ...(period ? { period } : {}) })
    }
  }

  private dispatch(raw: string): void {
    let msg: any
    try {
      msg = JSON.parse(raw)
    } catch {
      return
    }
    const ch = msg?.channel
    if (ch === 'ticker') {
      this.callbacks.onTicker?.(String(msg.symbol), msg.data as TickerItem)
    } else if (ch === 'kline') {
      this.callbacks.onKline?.(String(msg.symbol), String(msg.period), msg.data as KlineItem)
    }
    // 'subscribed' / 'error' / 'pong' 无需前端处理
  }

  private startHeartbeat(): void {
    this.stopHeartbeat()
    this.heartbeatTimer = window.setInterval(() => this.send({ op: 'ping' }), HEARTBEAT_MS)
  }

  private stopHeartbeat(): void {
    if (this.heartbeatTimer != null) {
      clearInterval(this.heartbeatTimer)
      this.heartbeatTimer = null
    }
  }

  private scheduleReconnect(): void {
    if (this.closedByUser || this.retryTimer != null) return
    const delay = this.retry
    this.retry = Math.min(this.retry * 2, MAX_RETRY)
    this.retryTimer = window.setTimeout(() => {
      this.retryTimer = null
      if (!this.closedByUser) this.open()
    }, delay)
  }
}

/** 单例：全站复用一条 ws 连接（页面路由互斥，同一时刻仅一个页面订阅） */
export const marketWs = new MarketWs()
