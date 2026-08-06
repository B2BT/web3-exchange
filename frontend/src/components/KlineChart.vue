<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import * as echarts from 'echarts'
import { kline } from '@/api/market'
import type { KlineItem } from '@/api/market'
import { formatLong } from '@/utils/format'

const props = defineProps<{
  symbol: string
  period: string
  /** 计价币精度（价格用） */
  quoteDecimals: number
  /** 基础币精度（成交量用） */
  baseDecimals: number
  priceDecimals?: number
}>()

const containerRef = ref<HTMLDivElement>()
const chart = shallowRef<echarts.ECharts | null>(null)
const loading = ref(false)
const empty = ref(false)
const errorMsg = ref('')
let resizeObserver: ResizeObserver | null = null

function formatTime(ms?: number | null): string {
  if (ms == null) return ''
  const d = new Date(ms)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function initChart() {
  if (!containerRef.value) return
  if (!chart.value) {
    chart.value = echarts.init(containerRef.value)
  }
  if (resizeObserver) resizeObserver.disconnect()
  resizeObserver = new ResizeObserver(() => chart.value?.resize())
  resizeObserver.observe(containerRef.value)
}

/** 计算简单移动平均线：前 n-1 个为 null（无值），第 n-1 个起为前 n 根收盘均值 */
function calcMA(closes: number[], n: number): (number | null)[] {
  const out: (number | null)[] = new Array(closes.length).fill(null)
  if (closes.length < n) return out
  let sum = 0
  for (let i = 0; i < closes.length; i++) {
    sum += closes[i]
    if (i >= n) sum -= closes[i - n]
    if (i >= n - 1) out[i] = sum / n
  }
  return out
}

function render(rows: KlineItem[]) {
  const c = chart.value
  if (!c) return
  if (!rows.length) {
    empty.value = true
    c.clear()
    return
  }
  empty.value = false
  const cats: string[] = []
  const ohlc: number[][] = []
  const vols: { value: number; itemStyle: { color: string } }[] = []
  const closes: number[] = []
  for (const r of rows) {
    cats.push(formatTime(r.openTime))
    ohlc.push([r.open ?? 0, r.close ?? 0, r.low ?? 0, r.high ?? 0])
    closes.push(r.close ?? 0)
    const up = (r.close ?? 0) >= (r.open ?? 0)
    vols.push({
      value: r.volume ?? 0,
      itemStyle: { color: up ? 'rgba(38,166,154,0.55)' : 'rgba(239,83,80,0.55)' },
    })
  }
  // eslint-disable-next-line no-console
  const ma5 = calcMA(closes, 5)
  const ma10 = calcMA(closes, 10)
  const ma20 = calcMA(closes, 20)
  const pd = props.priceDecimals ?? 8
  const qd = props.quoteDecimals
  const bd = props.baseDecimals

  // 数据量自适应：≤30 根全量显示；否则默认显示最近 60 根
  const n = rows.length
  const defaultEnd = 100
  const defaultStart = n <= 30 ? 0 : Math.max(0, 100 - (60 / n) * 100)

  // 价格轴范围（用基础数据，确保蜡烛不被压缩成一条线）
  let pMin = Infinity
  let pMax = -Infinity
  for (const k of ohlc) {
    pMin = Math.min(pMin, k[2])
    pMax = Math.max(pMax, k[3])
  }
  const pad = (pMax - pMin) * 0.08 || 1
  const yMin = pMin - pad
  const yMax = pMax + pad

  const tooltipFmt = (params: any[]) => {
    const p = params && params[0]
    if (!p) return ''
    const idx = p.dataIndex
    const k = ohlc[idx]
    // 价格统一用 priceDecimals(8)，成交量用 baseDecimals
    const fmt = (v: number | null | undefined, dec?: number) =>
      v == null ? '—' : formatLong(v, dec ?? pd, pd)
    return [
      `<div style="font-weight:600;margin-bottom:4px">${cats[idx]}</div>`,
      `开: <b>${fmt(k[0])}</b>`,
      `收: <b>${fmt(k[1])}</b>`,
      `低: <b>${fmt(k[2])}</b>`,
      `高: <b>${fmt(k[3])}</b>`,
      `量: ${fmt(vols[idx].value, bd)}`,
      `MA5: <span style="color:#f59e0b">${fmt(ma5[idx])}</span>`,
      `MA10: <span style="color:#60a5fa">${fmt(ma10[idx])}</span>`,
      `MA20: <span style="color:#a78bfa">${fmt(ma20[idx])}</span>`,
    ].join('<br/>')
  }

  c.setOption(
    {
      animation: false,
      axisPointer: { link: [{ xAxisIndex: 'all' }], label: { backgroundColor: '#3a3d51' } },
      tooltip: {
        trigger: 'axis',
        axisPointer: { type: 'cross' },
        backgroundColor: 'rgba(26,28,44,0.92)',
        borderColor: '#3a3d51',
        textStyle: { color: '#e5e7eb' },
        formatter: tooltipFmt,
      },
      legend: {
        top: 0,
        right: 8,
        textStyle: { color: '#909399', fontSize: 11 },
        itemWidth: 14,
        itemHeight: 8,
        data: ['MA5', 'MA10', 'MA20'],
      },
      grid: [
        { left: 64, right: 20, top: 32, height: '54%' },
        { left: 64, right: 20, top: '72%', height: '16%' },
      ],
      xAxis: [
        {
          type: 'category',
          data: cats,
          boundaryGap: true,
          axisLine: { lineStyle: { color: '#3a3d51' } },
          axisLabel: { color: '#909399' },
          splitLine: { show: false },
          min: 'dataMin',
          max: 'dataMax',
        },
        {
          type: 'category',
          gridIndex: 1,
          data: cats,
          boundaryGap: true,
          axisLine: { lineStyle: { color: '#3a3d51' } },
          axisLabel: { show: false },
          axisTick: { show: false },
        },
      ],
      yAxis: [
        {
          scale: true,
          min: 'dataMin',
          max: 'dataMax',
          splitLine: { lineStyle: { color: 'rgba(0,0,0,0.06)' } },
          axisLabel: { color: '#909399', formatter: (v: number) => formatLong(v, pd, 2) },
        },
        {
          gridIndex: 1,
          splitLine: { show: false },
          axisLabel: { show: false },
          axisLine: { show: false },
        },
      ],
      dataZoom: [
        { type: 'inside', xAxisIndex: [0, 1], start: defaultStart, end: defaultEnd },
        { type: 'slider', xAxisIndex: [0, 1], bottom: 6, height: 14, start: defaultStart, end: defaultEnd },
      ],
      series: [
        {
          name: 'K线',
          type: 'candlestick',
          data: ohlc,
          itemStyle: {
            color: '#26a69a',
            color0: '#ef5350',
            borderColor: '#26a69a',
            borderColor0: '#ef5350',
          },
        },
        {
          name: '成交量',
          type: 'bar',
          xAxisIndex: 1,
          yAxisIndex: 1,
          data: vols,
        },
        {
          name: 'MA5',
          type: 'line',
          data: ma5,
          smooth: true,
          symbol: 'none',
          lineStyle: { width: 1.2, color: '#f59e0b' },
        },
        {
          name: 'MA10',
          type: 'line',
          data: ma10,
          smooth: true,
          symbol: 'none',
          lineStyle: { width: 1.2, color: '#60a5fa' },
        },
        {
          name: 'MA20',
          type: 'line',
          data: ma20,
          smooth: true,
          symbol: 'none',
          lineStyle: { width: 1.2, color: '#a78bfa' },
        },
      ],
    },
    true, // notMerge：周期/交易对切换时完全重建，避免残留旧数据
  )
}

async function load() {
  initChart()
  loading.value = true
  empty.value = false
  errorMsg.value = ''
  try {
    const rows = await kline(props.symbol, props.period)
    render(rows)
  } catch (e: any) {
    errorMsg.value = e?.message || 'K线加载失败'
    chart.value?.clear()
  } finally {
    loading.value = false
  }
}

onMounted(load)
watch(() => [props.symbol, props.period], load)
onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  chart.value?.dispose()
  chart.value = null
})
</script>

<template>
  <div class="kline-wrap">
    <div v-loading="loading" class="kline-box" ref="containerRef"></div>
    <el-empty
      v-if="empty && !loading"
      description="暂无K线数据（需要行情种子数据）"
      :image-size="80"
    />
    <div v-if="errorMsg && !loading" class="kline-error">{{ errorMsg }}</div>
  </div>
</template>

<style scoped>
.kline-wrap {
  position: relative;
}
.kline-box {
  width: 100%;
  height: 460px;
}
.kline-error {
  padding: 24px;
  text-align: center;
  color: #e6a23c;
  font-size: 13px;
}
</style>
