/** 币种精度：Long 最小单位换算用（USDT=6, BTC=8, ETH=18） */
export const COIN_DECIMALS: Record<string, number> = {
  USDT: 6,
  BTC: 8,
  ETH: 18,
}

/** 价格统一展示 8 位小数 */
export const PRICE_DECIMALS = 8

/** 默认交易对（ticker 为空时的兜底下拉） */
export const DEFAULT_SYMBOLS: { symbol: string; base: string; quote: string }[] = [
  { symbol: 'BTC/USDT', base: 'BTC', quote: 'USDT' },
  { symbol: 'ETH/USDT', base: 'ETH', quote: 'USDT' },
]

/** K线周期切换 */
export const PERIODS: { value: string; label: string }[] = [
  { value: '1m', label: '1分' },
  { value: '5m', label: '5分' },
  { value: '15m', label: '15分' },
  { value: '1h', label: '1时' },
  { value: '1d', label: '1天' },
]

export function coinDecimals(coin: string): number {
  return COIN_DECIMALS[coin] ?? 6
}

export function symbolParts(symbol: string): { base: string; quote: string } {
  const idx = symbol.indexOf('/')
  if (idx < 0) return { base: symbol, quote: 'USDT' }
  return { base: symbol.slice(0, idx), quote: symbol.slice(idx + 1) }
}
