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
  const human = n / Math.pow(10, decimals)
  const fixed = human.toFixed(displayDecimals)
  return fixed.replace(/\.?0+$/, '') || '0'
}

/** 24h 涨跌幅：后端返回基点 bp（10000=100%），转百分比字符串 */
export function formatBpPercent(bp: number | string | null | undefined): string {
  if (bp === null || bp === undefined || bp === '') return '--'
  const n = typeof bp === 'string' ? Number(bp) : bp
  if (!Number.isFinite(n)) return '--'
  return (n / 100).toFixed(2) + '%'
}
