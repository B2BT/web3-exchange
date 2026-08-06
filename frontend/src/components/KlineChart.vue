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
  for (const r of rows) {
    cats.push(formatTime(r.openTime))
    ohlc.push([r.open ?? 0, r.close ?? 0, r.low ?? 0, r.high ?? 0])
    const up = (r.close ?? 0) >= (r.open ?? 0)
    vols.push({
      value: r.volume ?? 0,
      itemStyle: { color: up ? 'rgba(38,166,154,0.5)' : 'rgba(239,83,80,0.5)' },
    })
  }
  const pd = props.priceDecimals ?? 8
  const qd = props.quoteDecimals
  const bd = props.baseDecimals

  c.setOption({
    animation: false,
    axisPointer: {
      link: [{ xAxisIndex: 'all' }],
      label: { backgroundColor: '#3a3d51' },
    },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'cross' },
      backgroundColor: 'rgba(26,28,44,0.92)',
      borderColor: '#3a3d51',
      textStyle: { color: '#e5e7eb' },
      formatter: (params: any[]) => {
        const p = params && params[0]
        if (!p) return ''
        const idx = p.dataIndex
        const k = ohlc[idx]
        return [
          `<div style="font-weight:600;margin-bottom:4px">${cats[idx]}</div>`,
          `开: <b>${formatLong(k[0], qd, pd)}</b>`,
          `收: <b>${formatLong(k[1], qd, pd)}</b>`,
          `低: <b>${formatLong(k[2], qd, pd)}</b>`,
          `高: <b>${formatLong(k[3], qd, pd)}</b>`,
          `量: ${formatLong(vols[idx].value, bd, bd)}`,
        ].join('<br/>')
      },
    },
    grid: [
      { left: 64, right: 20, top: 24, height: '56%' },
      { left: 64, right: 20, top: '72%', height: '15%' },
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
        splitLine: { lineStyle: { color: 'rgba(0,0,0,0.06)' } },
        axisLabel: {
          color: '#909399',
          formatter: (v: number) => formatLong(v, qd, pd),
        },
      },
      {
        gridIndex: 1,
        splitLine: { show: false },
        axisLabel: { show: false },
        axisLine: { show: false },
      },
    ],
    dataZoom: [
      { type: 'inside', xAxisIndex: [0, 1], start: 50, end: 100 },
      { type: 'slider', xAxisIndex: [0, 1], bottom: 6, height: 14, start: 50, end: 100 },
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
    ],
  })
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
