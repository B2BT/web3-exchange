<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import * as echarts from 'echarts'
import { depth } from '@/api/market'
import type { DepthData } from '@/api/market'
import { formatAdaptivePrice } from '@/utils/format'

const props = defineProps<{
  symbol: string
  /** 价格精度（人读小数位数） */
  priceDecimals?: number
  /** 数量精度（基础币小数位） */
  baseDecimals?: number
}>()

const chartRef = ref<HTMLDivElement>()
const chart = shallowRef<echarts.ECharts | null>(null)
let resizeObserver: ResizeObserver | null = null
let timer = 0

function initChart() {
  if (!chartRef.value) return
  if (!chart.value) {
    chart.value = echarts.init(chartRef.value)
  }
  if (resizeObserver) resizeObserver.disconnect()
  resizeObserver = new ResizeObserver(() => chart.value?.resize())
  resizeObserver.observe(chartRef.value)
}

async function load() {
  initChart()
  const c = chart.value
  if (!c) return
  try {
    const data = await depth(props.symbol, 20)
    c.setOption(buildOption(data), true)
  } catch (e) {
    // eslint-disable-next-line no-console
    console.error('[DepthChart] 渲染失败:', e)
  }
}

function buildOption(data: DepthData) {
  const priceDec = props.priceDecimals ?? 8
  const baseDec = props.baseDecimals ?? 8
  const bids = data.bids ?? []
  const asks = data.asks ?? []
  const bidNums = bids.map((b) => Number(b.price))
  const askNums = asks.map((a) => Number(a.price))
  const bestBid = bidNums.length ? Math.max(...bidNums) : 0
  const bestAsk = askNums.length ? Math.min(...askNums) : 0

  // 过滤离群价格：只保留与最优价差距在 50% 内的档位，避免异常数据拉爆坐标
  const bidLevels = bids
    .filter((b) => bestBid === 0 || Math.abs(Number(b.price) - bestBid) / bestBid <= 0.5)
    .sort((a, b) => Number(a.price) - Number(b.price)) // 升序：低价在左
  const askLevels = asks
    .filter((a) => bestAsk === 0 || Math.abs(Number(a.price) - bestAsk) / bestAsk <= 0.5)
    .sort((a, b) => Number(a.price) - Number(b.price)) // 升序：低价在左

  // 买盘：从左(最低价)到右(最高价)，累计 = 该价格及以上所有买单量
  // 数量用「最小单位」累加显示（除以 baseDec 会归一化到 ~0.001 不可见）
  let bidCum = 0
  const bidData = bidLevels.map((b) => {
    bidCum += Number(b.quantity)
    return [Number(b.price) / Math.pow(10, priceDec), bidCum]
  })
  // 卖盘：从左(最低价)到右(最高价)，累计 = 该价格及以下所有卖单量
  let askCum = 0
  const askData = askLevels.map((a) => {
    askCum += Number(a.quantity)
    return [Number(a.price) / Math.pow(10, priceDec), askCum]
  })

  const midPrice = bestBid && bestAsk ? (bestBid + bestAsk) / 2 : 0

  return {
    backgroundColor: 'transparent',
    animation: false,
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'cross' },
      formatter: (params: any) => {
        const p = params[0]
        if (!p || !p.value) return ''
        return `价格: ${formatAdaptivePrice(Number(p.value[0]))}<br/>累计量: ${Number(p.value[1]).toLocaleString()}`
      },
    },
    grid: { left: 60, right: 20, top: 24, bottom: 24 },
    xAxis: {
      type: 'value',
      scale: true,
      axisLabel: { color: '#8b93a7', fontSize: 11 },
      splitLine: { lineStyle: { color: 'rgba(255,255,255,0.05)' } },
      markLine: midPrice
        ? {
            silent: true,
            symbol: 'none',
            lineStyle: { color: '#22d3ee', width: 1, type: 'dashed' },
            label: { formatter: '当前价', color: '#22d3ee', fontSize: 10 },
            data: [{ xAxis: midPrice / Math.pow(10, priceDec) }],
          }
        : undefined,
    },
    yAxis: {
      type: 'value',
      name: '累计量',
      nameTextStyle: { color: '#8b93a7' },
      axisLabel: { color: '#8b93a7', fontSize: 11 },
      splitLine: { lineStyle: { color: 'rgba(255,255,255,0.05)' } },
    },
    series: [
      {
        name: '买盘',
        type: 'line',
        data: bidData,
        step: 'end',
        smooth: false,
        symbol: 'none',
        lineStyle: { color: '#22c55e', width: 2 },
        areaStyle: { color: 'rgba(34,197,94,0.25)' },
      },
      {
        name: '卖盘',
        type: 'line',
        data: askData,
        step: 'start',
        smooth: false,
        symbol: 'none',
        lineStyle: { color: '#ef4444', width: 2 },
        areaStyle: { color: 'rgba(239,68,68,0.25)' },
      },
    ],
  }
}

function render() {
  load()
  clearTimeout(timer)
  timer = window.setTimeout(load, 5000)
}

watch(() => props.symbol, () => { render() })

onMounted(async () => {
  await nextTick()
  initChart()
  render()
})

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  clearTimeout(timer)
  chart.value?.dispose()
  chart.value = null
})

defineExpose({ reload: render })
</script>

<template>
  <div ref="chartRef" class="depth-chart"></div>
</template>

<style scoped>
.depth-chart {
  width: 100%;
  height: 260px;
}
</style>
