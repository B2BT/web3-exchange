<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import KlineChart from '@/components/KlineChart.vue'
import { tickerList } from '@/api/market'
import type { TickerItem } from '@/api/market'
import { DEFAULT_SYMBOLS, PERIODS, coinDecimals, symbolParts } from '@/config/market'
import { formatBpPercent, formatLong } from '@/utils/format'

type SymbolOpt = { symbol: string; base: string; quote: string }

const symbols = ref<SymbolOpt[]>([])
const currentSymbol = ref('BTC/USDT')
const period = ref('1m')
const tickers = ref<TickerItem[]>([])
const tickerLoading = ref(false)
const errorMsg = ref('')

const parts = computed(() => symbolParts(currentSymbol.value))
const quoteDecimals = computed(() => coinDecimals(parts.value.quote))
const baseDecimals = computed(() => coinDecimals(parts.value.base))
/** 价格统一精度（交易对价格 8 位小数） */
const PRICE_DEC = 8

const currentTicker = computed<TickerItem | null>(
  () => tickers.value.find((t) => t.symbol === currentSymbol.value) || null,
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

onMounted(loadTickers)
</script>

<template>
  <div class="market-page">
    <!-- 顶部：交易对选择 + 周期切换 -->
    <el-card shadow="never" class="toolbar-card g-card">
      <div class="toolbar">
        <el-select v-model="currentSymbol" style="width: 180px" placeholder="选择交易对">
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
          刷新
        </el-button>
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
    <el-card shadow="never" class="ticker-card g-card">
      <el-row :gutter="16" v-if="currentTicker">
        <el-col :span="6">
          <div class="tick-label">{{ currentSymbol }} 最新价</div>
          <div class="tick-value" :class="changeClass">
            {{ formatLong(currentTicker.lastPrice, PRICE_DEC, 6) }}
          </div>
        </el-col>
        <el-col :span="4">
          <div class="tick-label">24h涨跌</div>
          <div class="tick-value" :class="changeClass">
            {{ formatBpPercent(currentTicker.change24h) }}
          </div>
        </el-col>
        <el-col :span="4">
          <div class="tick-label">24h最高</div>
          <div class="tick-value">{{ formatLong(currentTicker.high24h, PRICE_DEC, 6) }}</div>
        </el-col>
        <el-col :span="4">
          <div class="tick-label">24h最低</div>
          <div class="tick-value">{{ formatLong(currentTicker.low24h, PRICE_DEC, 6) }}</div>
        </el-col>
        <el-col :span="3">
          <div class="tick-label">24h量</div>
          <div class="tick-value">{{ formatLong(currentTicker.volume24h, baseDecimals, 6) }}</div>
        </el-col>
        <el-col :span="3">
          <div class="tick-label">24h额</div>
          <div class="tick-value">{{ formatLong(currentTicker.quoteVolume24h, quoteDecimals, 4) }}</div>
        </el-col>
      </el-row>
      <el-empty
        v-else
        description="暂无该交易对行情（缺少行情种子数据，K线/价格显示为空）"
        :image-size="70"
      />
    </el-card>

    <!-- K线图 -->
    <el-card shadow="never" class="kline-card g-card">
      <template #header>
        <div class="kline-header">
          <span class="kline-title">{{ currentSymbol }} 价格走势（{{ period }}）</span>
        </div>
      </template>
      <KlineChart
        :symbol="currentSymbol"
        :period="period"
        :quote-decimals="quoteDecimals"
        :base-decimals="baseDecimals"
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
</style>
