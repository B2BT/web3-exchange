<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import KlineChart from '@/components/KlineChart.vue'
import DepthChart from '@/components/DepthChart.vue'
import { tickerList } from '@/api/market'
import type { KlineItem, TickerItem } from '@/api/market'
import { marketWs } from '@/api/marketWs'
import { DEFAULT_SYMBOLS, PERIODS, coinDecimals, symbolParts } from '@/config/market'
import { formatBpPercent, formatLong } from '@/utils/format'

const { t } = useI18n()

type SymbolOpt = { symbol: string; base: string; quote: string }

const symbols = ref<SymbolOpt[]>([])
const currentSymbol = ref('BTC/USDT')
const period = ref('1m')
const tickers = ref<TickerItem[]>([])
const tickerLoading = ref(false)
const errorMsg = ref('')
/** ws 实时推送的最新一根 K线（喂给 KlineChart 实时刷新） */
const liveKline = ref<KlineItem | null>(null)
/** ws 连接状态（false 时走 REST 兜底） */
const wsReady = ref(false)

const parts = computed(() => symbolParts(currentSymbol.value))
const quoteDecimals = computed(() => coinDecimals(parts.value.quote))
const baseDecimals = computed(() => coinDecimals(parts.value.base))
/** 价格统一精度（交易对价格 8 位小数） */
const PRICE_DEC = 8

const currentTicker = computed<TickerItem | null>(
  () => tickers.value.find((t) => t.symbol === currentSymbol.value) || null,
)

/** 最新价闪烁 class（涨绿/跌红，更新时触发） */
const priceFlash = ref('')
let flashTimer = 0
watch(
  () => currentTicker.value?.lastPrice,
  (n, o) => {
    if (n == null || o == null || n === o) return
    priceFlash.value = Number(n) >= Number(o) ? 'flash-up' : 'flash-down'
    clearTimeout(flashTimer)
    flashTimer = window.setTimeout(() => (priceFlash.value = ''), 700)
  },
)

async function loadTickers() {
  tickerLoading.value = true
  try {
    const res = await tickerList()
    tickers.value = Array.isArray(res) ? res : []
    // 交易对来源：ticker 里的 symbol + 兜底默认对
    const fromTicker = Array.from(
      new Set(tickers.value.map((t) => t.symbol).filter((s): s is string => !!s)),
    )
    const merged: string[] = []
    for (const d of DEFAULT_SYMBOLS) if (!fromTicker.includes(d.symbol)) merged.push(d.symbol)
    for (const s of fromTicker) if (!merged.includes(s)) merged.push(s)
    symbols.value = merged.map((s) => ({ symbol: s, ...symbolParts(s) }))
    if (symbols.value.length && !symbols.value.some((s) => s.symbol === currentSymbol.value)) {
      currentSymbol.value = symbols.value[0].symbol
    }
  } catch (e: any) {
    errorMsg.value = e?.message || '行情加载失败'
  } finally {
    tickerLoading.value = false
  }
}

const changeClass = computed(() => {
  const bp = currentTicker.value?.change24h
  if (bp == null) return ''
  return Number(bp) >= 0 ? 'up' : 'down'
})

/* ================= WebSocket 实时行情 ================= */
function subscribeWs() {
  marketWs.subscribe('ticker', currentSymbol.value)
  marketWs.subscribe('kline', currentSymbol.value, period.value)
  liveKline.value = null
}

function unsubscribeWs() {
  marketWs.unsubscribe('ticker', currentSymbol.value)
  marketWs.unsubscribe('kline', currentSymbol.value, period.value)
}

/** ticker 推送 → 合并进列表（更新或新增） */
function applyTicker(data: TickerItem) {
  const sym = data.symbol || currentSymbol.value
  const idx = tickers.value.findIndex((t) => t.symbol === sym)
  if (idx >= 0) {
    tickers.value[idx] = { ...tickers.value[idx], ...data }
  } else {
    tickers.value.push({ ...data, symbol: sym })
  }
  // 触发响应式，驱动 currentTicker 刷新
  tickers.value = [...tickers.value]
}

onMounted(() => {
  loadTickers()
  marketWs
    .on({
      onTicker: (symbol, data) => {
        if (symbol !== currentSymbol.value) return
        applyTicker(data)
      },
      onKline: (symbol, p, data) => {
        if (symbol !== currentSymbol.value || p !== period.value) return
        liveKline.value = data
      },
      onStatus: (connected) => {
        wsReady.value = connected
      },
    })
    .connect()
  subscribeWs()
})

watch(currentSymbol, (n, o) => {
  if (o) {
    marketWs.unsubscribe('ticker', o)
    marketWs.unsubscribe('kline', o, period.value)
  }
  subscribeWs()
})

watch(period, (n, o) => {
  marketWs.unsubscribe('kline', currentSymbol.value, o)
  marketWs.subscribe('kline', currentSymbol.value, n)
  liveKline.value = null
})

onBeforeUnmount(() => {
  unsubscribeWs()
  marketWs.close()
  clearTimeout(flashTimer)
})
</script>

<template>
  <div class="market-page">
    <!-- 顶部：交易对选择 + 周期切换 -->
    <el-card shadow="never" class="toolbar-card g-card">
      <div class="toolbar">
        <el-select v-model="currentSymbol" style="width: 180px" :placeholder="t('market.selectPair')">
          <el-option
            v-for="s in symbols"
            :key="s.symbol"
            :label="s.symbol"
            :value="s.symbol"
          />
        </el-select>

        <el-button-group class="period-group">
          <el-button
            v-for="p in PERIODS"
            :key="p.value"
            size="small"
            :type="period === p.value ? 'primary' : 'default'"
            @click="period = p.value"
          >
            {{ p.label }}
          </el-button>
        </el-button-group>

        <el-button
          size="default"
          :loading="tickerLoading"
          @click="loadTickers"
          style="margin-left: auto"
        >
          {{ t('common.refresh') }}
        </el-button>

        <el-tag
          v-if="wsReady"
          type="success"
          size="small"
          effect="plain"
          class="ws-tag"
        >
          {{ t('market.wsRealtime') }}
        </el-tag>
        <el-tag v-else type="info" size="small" effect="plain" class="ws-tag">
          {{ t('market.restFallback') }}
        </el-tag>
      </div>

      <el-alert
        v-if="errorMsg"
        :title="'行情接口暂不可用：' + errorMsg"
        type="warning"
        :closable="false"
        show-icon
        style="margin-top: 12px"
      />
    </el-card>

    <!-- ticker 卡片 -->
    <el-card shadow="never" class="ticker-card g-card tilt3d">
      <el-row :gutter="16" v-if="currentTicker">
        <el-col :span="6" :xs="24" :sm="6">
          <div class="tick-label">{{ currentSymbol }} {{ t('market.latestPrice') }}</div>
          <div class="tick-value breathe" :class="[changeClass, priceFlash]">
            {{ formatLong(currentTicker.lastPrice, PRICE_DEC, 6) }}
          </div>
        </el-col>
        <el-col :span="4" :xs="8" :sm="4">
          <div class="tick-label">{{ t('market.change24h') }}</div>
          <div class="tick-value" :class="changeClass">
            {{ formatBpPercent(currentTicker.change24h) }}
          </div>
        </el-col>
        <el-col :span="4" :xs="8" :sm="4">
          <div class="tick-label">{{ t('market.high24h') }}</div>
          <div class="tick-value">{{ formatLong(currentTicker.high24h, PRICE_DEC, 6) }}</div>
        </el-col>
        <el-col :span="4" :xs="8" :sm="4">
          <div class="tick-label">{{ t('market.low24h') }}</div>
          <div class="tick-value">{{ formatLong(currentTicker.low24h, PRICE_DEC, 6) }}</div>
        </el-col>
        <el-col :span="3" :xs="12" :sm="3">
          <div class="tick-label">{{ t('market.volume24h') }}</div>
          <div class="tick-value">{{ formatLong(currentTicker.volume24h, 8, 2) }}</div>
        </el-col>
        <el-col :span="3" :xs="12" :sm="3">
          <div class="tick-label">{{ t('market.quoteVolume24h') }}</div>
          <div class="tick-value">{{ formatLong(currentTicker.quoteVolume24h, 8, 2) }}</div>
        </el-col>
      </el-row>
      <el-empty
        v-else
        description="暂无该交易对行情（缺少行情种子数据，K线/价格显示为空）"
        :image-size="70"
      />
    </el-card>

    <!-- K线图 -->
    <el-card shadow="never" class="kline-card g-card scan-wrap">
      <template #header>
        <div class="kline-header">
          <span class="kline-title">{{ currentSymbol }} {{ t('market.priceTrend') }}（{{ period }}）</span>
        </div>
      </template>
      <KlineChart
        :symbol="currentSymbol"
        :period="period"
        :quote-decimals="quoteDecimals"
        :base-decimals="baseDecimals"
        :live-kline="liveKline"
      />
    </el-card>

    <!-- 市场深度图 -->
    <el-card shadow="never" class="depth-card g-card">
      <template #header>
        <div class="kline-header">
          <span class="kline-title">{{ currentSymbol }} {{ t('market.marketDepth') }}</span>
        </div>
      </template>
      <DepthChart
        :symbol="currentSymbol"
        :price-decimals="PRICE_DEC"
        :base-decimals="baseDecimals"
        :key="'depth-' + currentSymbol"
      />
    </el-card>
  </div>
</template>

<style scoped>
.market-page {
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.toolbar {
  display: flex;
  align-items: center;
  gap: 16px;
}
.period-group {
  margin-left: 8px;
}
.ws-tag {
  margin-left: 10px;
}
.tick-label {
  font-size: 12px;
  color: var(--text-dim);
  margin-bottom: 6px;
}
.tick-value {
  font-size: 20px;
  font-weight: 700;
  color: var(--text-primary);
  font-family: var(--font-num);
  font-variant-numeric: tabular-nums;
  text-shadow: 0 0 12px rgba(255, 255, 255, 0.08);
}
.tick-value.up {
  color: var(--up);
  text-shadow: 0 0 16px rgba(34, 197, 94, 0.4);
}
.tick-value.down {
  color: var(--down);
  text-shadow: 0 0 16px rgba(239, 68, 68, 0.4);
}
.kline-title {
  font-weight: 600;
}

/* ================= 移动端 ================= */
@media (max-width: 767px) {
  .market-page {
    padding: 10px;
    gap: 12px;
  }
  .toolbar {
    flex-wrap: wrap;
    gap: 10px;
  }
  .toolbar .el-select {
    width: 100% !important;
    flex: 1 1 100%;
  }
  .period-group {
    margin-left: 0;
    order: 3;
    width: 100%;
  }
  .period-group .el-button {
    flex: 1;
    margin-left: 0 !important;
  }
  .toolbar > .el-button {
    margin-left: auto !important;
    order: 2;
  }
  .ws-tag {
    margin-left: 0;
    order: 4;
  }
  .tick-value {
    font-size: 17px;
  }
}
</style>
