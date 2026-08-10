/** Long 最小单位 → 人读数字 */
export function toLong(value: number, decimals: number): number {
  return Math.round(value * Math.pow(10, decimals))
}

/**
 * Long 最小单位 → 展示字符串。
 * @param value 后端最小单位数值
 * @param decimals 币种精度（最小单位位数）
 * @param displayDecimals 展示小数位（默认与精度一致）
 */
export function formatLong(
  value: number | string | null | undefined,
  decimals: number,
  displayDecimals: number = decimals,
): string {
  if (value === null || value === undefined || value === '') return '--'
  const n = typeof value === 'string' ? Number(value) : value
  if (!Number.isFinite(n)) return '--'
  // 先做浮点稳定化（避免 Long/1e^decimals 除法后的尾部噪音），再定点显示
  const human = n / Math.pow(10, decimals)
  const factor = Math.pow(10, displayDecimals)
  const rounded = Math.round(human * factor) / factor
  const fixed = rounded.toFixed(displayDecimals)
  return fixed.replace(/\.?0+$/, '') || '0'
}

/** 24h 涨跌幅：后端返回基点 bp（10000=100%），转百分比字符串 */
export function formatBpPercent(bp: number | string | null | undefined): string {
  if (bp === null || bp === undefined || bp === '') return '--'
  const n = typeof bp === 'string' ? Number(bp) : bp
  if (!Number.isFinite(n)) return '--'
  return (n / 100).toFixed(2) + '%'
}

/**
 * 自适应精度价格格式化：按价格量级自动选小数位。
 * - ≥1000：2 位（BTC/ETH 级别，$65064.12）
 * - ≥1：4 位（中价币，$1.9154）
 * - ≥0.01：6 位（低价币，$0.0696）
 * - <0.01：8 位（极低价，$0.00001234）
 * 精度上限 8 位（与后端价格最小单位一致）。
 */
export function formatAdaptivePrice(value: number | string | null | undefined): string {
  if (value === null || value === undefined || value === '') return '--'
  const n = typeof value === 'string' ? Number(value) : value
  if (!Number.isFinite(n)) return '--'
  const abs = Math.abs(n)
  let dec = 2
  if (abs >= 1000) dec = 2
  else if (abs >= 1) dec = 4
  else if (abs >= 0.01) dec = 6
  else dec = 8
  return n.toFixed(dec).replace(/\.?0+$/, '') || '0'
}
