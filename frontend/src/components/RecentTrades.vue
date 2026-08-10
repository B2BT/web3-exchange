<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { recentTrades } from '@/api/order'
import type { RecentTradeItem } from '@/api/order'
import { formatAdaptivePrice, formatLong } from '@/utils/format'

const props = withDefaults(
  defineProps<{
    symbol: string
    quoteSymbol: string
    quoteDecimals: number
    baseDecimals: number
    /** 价格精度（小数位，默认 8） */
    priceDecimals?: number
    /** 展示小数位（价格） */
    priceDisplayDecimals?: number
  }>(),
  { priceDecimals: 8, priceDisplayDecimals: 2 },
)

const loading = ref(false)
const errorMsg = ref('')
const rows = ref<RecentTradeItem[]>([])

let timer: ReturnType<typeof setInterval> | null = null

function priceClass(t: RecentTradeItem, idx: number): string {
  // takerSide：1=主动买(涨/绿) 2=主动卖(跌/红)；缺失时与上一档比较
  if (t.takerSide === 1) return 'up'
  if (t.takerSide === 2) return 'down'
  const prev = idx + 1 < rows.value.length ? rows.value[idx + 1].price : null
  if (prev == null || t.price == null) return ''
  return t.price > prev ? 'up' : t.price < prev ? 'down' : ''
}

async function load() {
  loading.value = true
  errorMsg.value = ''
  try {
    const res = await recentTrades({ symbol: props.symbol, limit: 50 })
    rows.value = Array.isArray(res) ? res : []
  } catch (e: any) {
    errorMsg.value = e?.message || '成交加载失败'
  } finally {
    loading.value = false
  }
}

function formatTime(t?: string): string {
  if (!t) return '--'
  // 兼容 ISO 与 "yyyy-MM-dd HH:mm:ss"
  const d = new Date(t)
  if (Number.isNaN(d.getTime())) return t
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

watch(() => props.symbol, load)

onMounted(() => {
  load()
  timer = setInterval(load, 3000)
})
onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
})
</script>

<template>
  <div class="rt-panel">
    <div class="rt-header">
      <span>最近成交</span>
      <el-button text size="small" :loading="loading" @click="load">刷新</el-button>
    </div>

    <el-alert
      v-if="errorMsg"
      :title="'成交接口暂不可用：' + errorMsg"
      type="warning"
      :closable="false"
      show-icon
      style="margin-bottom: 8px"
    />

    <div v-else class="rt-body">
      <div class="rt-head-row">
        <span>时间</span>
        <span>价格({{ quoteSymbol }})</span>
        <span>数量</span>
      </div>

      <el-scrollbar max-height="440px" class="rt-scroll">
        <template v-if="rows.length">
          <div v-for="(t, i) in rows" :key="(t.id ?? t.tradeNo ?? i) + '_' + i" class="rt-row">
            <span class="rt-time">{{ formatTime(t.tradeTime) }}</span>
            <span class="rt-price num" :class="priceClass(t, i)">
              {{ formatAdaptivePrice(t.price == null ? null : t.price / Math.pow(10, priceDecimals)) }}
            </span>
            <span class="rt-qty num">{{ formatLong(t.quantity, baseDecimals, baseDecimals) }}</span>
          </div>
        </template>
        <el-empty v-else description="暂无成交记录" :image-size="60" />
      </el-scrollbar>
    </div>
  </div>
</template>

<style scoped>
.rt-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}
.rt-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
  margin-bottom: 8px;
}
.rt-body {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}
.rt-head-row {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: var(--text-dim);
  padding: 2px 8px;
  border-bottom: 1px solid var(--glass-border);
  margin-bottom: 4px;
}
.rt-head-row span:nth-child(2) {
  flex: 1;
  text-align: right;
  margin-right: 4px;
}
.rt-row {
  display: flex;
  justify-content: space-between;
  padding: 3px 8px;
  font-size: 12px;
}
.rt-row:hover {
  background: rgba(255, 255, 255, 0.04);
}
.rt-time {
  color: var(--text-secondary);
}
.rt-price {
  flex: 1;
  text-align: right;
  margin-right: 4px;
  font-weight: 600;
}
.rt-price.up {
  color: var(--up);
}
.rt-price.down {
  color: var(--down);
}
.rt-qty {
  color: var(--text-secondary);
}
.rt-scroll {
  flex: 1;
  min-height: 0;
}
</style>
