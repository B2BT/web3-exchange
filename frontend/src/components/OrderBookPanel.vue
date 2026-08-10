<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { depth } from '@/api/market'
import type { DepthData, DepthLevel } from '@/api/market'
import { formatAdaptivePrice, formatLong } from '@/utils/format'

const props = withDefaults(
  defineProps<{
    symbol: string
    quoteSymbol: string
    quoteDecimals: number
    baseDecimals: number
    /** 最新价（人读数值），显示在卖买盘之间 */
    lastPrice?: number | null
    /** 价格精度（小数位，默认 8） */
    priceDecimals?: number
    /** 展示小数位（价格） */
    priceDisplayDecimals?: number
  }>(),
  { lastPrice: null, priceDecimals: 8, priceDisplayDecimals: 2 },
)

const emit = defineEmits<{
  (e: 'select-price', price: string): void
}>()

const loading = ref(false)
const errorMsg = ref('')
const asks = ref<DepthLevel[]>([])
const bids = ref<DepthLevel[]>([])

/** 展示用小数位：价格默认跟随计价币精度，避免过长（保留 prop 供外部指定，展示已改自适应） */
/** 盘口价格：按量级自适应精度（min-unit → 人读 → 自适应小数位） */
const fmtPrice = (v: number | null | undefined): string =>
  v == null ? '--' : formatAdaptivePrice(v / Math.pow(10, props.priceDecimals))

const bestAsk = computed<number | null>(() => asks.value[0]?.price ?? null)
const bestBid = computed<number | null>(() => bids.value[0]?.price ?? null)
/** 盘口中间价（优先最新价，其次 best bid/ask 中值） */
const midPrice = computed<number | null>(() => {
  if (props.lastPrice != null) return props.lastPrice
  if (bestAsk.value != null && bestBid.value != null) return (bestAsk.value + bestBid.value) / 2
  return bestAsk.value ?? bestBid.value
})

let timer: ReturnType<typeof setInterval> | null = null

async function load() {
  loading.value = true
  errorMsg.value = ''
  try {
    const res: DepthData = await depth(props.symbol, 15)
    // 防御性排序：asks 升序（最低价在上）、bids 降序（最高价在上）
    const a = (Array.isArray(res?.asks) ? res.asks : []).slice(0, 15)
    const b = (Array.isArray(res?.bids) ? res.bids : []).slice(0, 15)
    asks.value = a.sort((x, y) => (x.price ?? 0) - (y.price ?? 0))
    bids.value = b.sort((x, y) => (y.price ?? 0) - (x.price ?? 0))
  } catch (e: any) {
    errorMsg.value = e?.message || '盘口加载失败'
  } finally {
    loading.value = false
  }
}

function onRowClick(price: number | null | undefined) {
  if (price == null) return
  emit('select-price', formatLong(price, props.priceDecimals, props.priceDecimals))
}

watch(() => props.symbol, load)

onMounted(() => {
  load()
  // 跟随成交刷新盘口
  timer = setInterval(load, 3000)
})
onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
})
</script>

<template>
  <div class="ob-panel">
    <div class="ob-header">
      <span>深度盘口</span>
      <el-button text size="small" :loading="loading" @click="load">刷新</el-button>
    </div>

    <el-alert
      v-if="errorMsg"
      :title="'盘口接口暂不可用：' + errorMsg"
      type="warning"
      :closable="false"
      show-icon
      style="margin-bottom: 8px"
    />

    <div v-else class="ob-body" :class="{ 'is-empty': !asks.length && !bids.length }">
      <div class="ob-head-row">
        <span>价格({{ quoteSymbol }})</span>
        <span>数量</span>
      </div>

      <!-- 卖盘：价格升序（最低价在上，靠近中间价）红色 -->
      <div class="ob-asks">
        <button
          v-for="(lvl, i) in asks"
          :key="'a' + i + '_' + lvl.price"
          class="ob-row ask"
          type="button"
          @click="onRowClick(lvl.price)"
        >
          <span class="ob-price num">{{ fmtPrice(lvl.price) }}</span>
          <span class="ob-qty num">{{ formatLong(lvl.quantity, baseDecimals, baseDecimals) }}</span>
        </button>
      </div>

      <!-- 中间最新价 -->
      <div class="ob-mid" v-if="midPrice != null">
        <span class="num">{{ fmtPrice(midPrice) }}</span>
      </div>

      <!-- 买盘：价格降序（最高价在上，靠近中间价）绿色 -->
      <div class="ob-bids">
        <button
          v-for="(lvl, i) in bids"
          :key="'b' + i + '_' + lvl.price"
          class="ob-row bid"
          type="button"
          @click="onRowClick(lvl.price)"
        >
          <span class="ob-price num">{{ fmtPrice(lvl.price) }}</span>
          <span class="ob-qty num">{{ formatLong(lvl.quantity, baseDecimals, baseDecimals) }}</span>
        </button>
      </div>

      <div v-if="!asks.length && !bids.length" class="ob-empty">暂无盘口数据</div>
    </div>
  </div>
</template>

<style scoped>
.ob-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}
.ob-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
  margin-bottom: 8px;
}
.ob-body {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}
.ob-head-row {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: var(--text-dim);
  padding: 2px 8px;
  border-bottom: 1px solid var(--glass-border);
  margin-bottom: 4px;
}
.ob-row {
  display: flex;
  justify-content: space-between;
  width: 100%;
  background: transparent;
  border: none;
  padding: 3px 8px;
  cursor: pointer;
  color: var(--text-primary);
  font-size: 12px;
  text-align: left;
}
.ob-row:hover {
  background: rgba(255, 255, 255, 0.06);
}
.ob-price.ask {
  color: var(--down);
}
.ob-price.bid {
  color: var(--up);
}
.ob-qty {
  color: var(--text-secondary);
}
.ob-mid {
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 6px 0;
  border-top: 1px solid var(--glass-border);
  border-bottom: 1px solid var(--glass-border);
  font-weight: 700;
  color: var(--text-primary);
  background: rgba(255, 255, 255, 0.03);
}
.ob-empty {
  text-align: center;
  color: var(--text-dim);
  padding: 24px 0;
  font-size: 13px;
}
.ob-body.is-empty .ob-head-row {
  display: none;
}
</style>
