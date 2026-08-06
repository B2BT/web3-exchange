<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { placeOrder } from '@/api/order'
import type { PlaceOrderResult } from '@/api/order'
import { accounts } from '@/api/asset'
import { tickerList } from '@/api/market'
import { marketWs } from '@/api/marketWs'
import { useAuthStore } from '@/stores/auth'
import { DEFAULT_SYMBOLS, PRICE_DECIMALS, coinDecimals, symbolParts } from '@/config/market'
import { formatLong, toLong } from '@/utils/format'
import OrderBookPanel from '@/components/OrderBookPanel.vue'
import RecentTrades from '@/components/RecentTrades.vue'

const authStore = useAuthStore()

const symbols = DEFAULT_SYMBOLS
const currentSymbol = ref('BTC/USDT')
/** 方向：1=买入 2=卖出 */
const side = ref<number>(1)
/** 类型：1=限价 2=市价 */
const orderType = ref<number>(1)
/** 时间策略：0=GTC 1=IOC 2=FOK 3=PostOnly（默认 GTC） */
const timeInForce = ref<number>(0)
/** 时间策略选项 */
const TIME_IN_FORCE_OPTIONS = [
  { label: 'GTC', value: 0 },
  { label: 'IOC', value: 1 },
  { label: 'FOK', value: 2 },
  { label: 'PostOnly', value: 3 },
]
const price = ref('')
const quantity = ref('')
const quoteAmount = ref('')
const submitting = ref(false)

const parts = computed(() => symbolParts(currentSymbol.value))
const quoteDecimals = computed(() => coinDecimals(parts.value.quote))
const baseDecimals = computed(() => coinDecimals(parts.value.base))
const isBuy = computed(() => side.value === 1)

/* ================= 行情 & 余额 ================= */
/** 最新价（Long 最小单位） */
const lastPriceLong = ref<number | null>(null)
/** 可用余额（人读数值）：buy→计价币，sell→基础币 */
const availableQuote = ref(0)
const availableBase = ref(0)
const balanceLoaded = ref(false)

const lastPriceHuman = computed<number | null>(() =>
  lastPriceLong.value != null ? lastPriceLong.value / Math.pow(10, PRICE_DECIMALS) : null,
)

const availableCoinName = computed(() => (isBuy.value ? parts.value.quote : parts.value.base))
const availableValue = computed(() => (isBuy.value ? availableQuote.value : availableBase.value))
const availableLabel = computed(() => {
  if (!balanceLoaded.value) return `可用 ${availableCoinName.value}: --`
  const dec = isBuy.value ? quoteDecimals.value : baseDecimals.value
  return `可用 ${availableCoinName.value}: ${fmtHuman(availableValue.value, dec)}`
})

async function loadTicker() {
  try {
    const res = await tickerList()
    const arr = Array.isArray(res) ? res : []
    const t = arr.find((x) => x.symbol === currentSymbol.value)
    if (t && t.lastPrice != null) lastPriceLong.value = t.lastPrice
  } catch {
    // 行情接口不可用时静默，最新价留空
  }
}

async function loadBalance() {
  const userId = authStore.userInfo?.id
  if (!userId) {
    balanceLoaded.value = false
    availableQuote.value = 0
    availableBase.value = 0
    return
  }
  try {
    const list = await accounts(userId)
    const arr = Array.isArray(list) ? list : []
    const qa = arr.find((a) => a.symbol === parts.value.quote)
    const ba = arr.find((a) => a.symbol === parts.value.base)
    availableQuote.value =
      qa?.available != null ? Number(qa.available) / Math.pow(10, quoteDecimals.value) : 0
    availableBase.value =
      ba?.available != null ? Number(ba.available) / Math.pow(10, baseDecimals.value) : 0
  } catch {
    availableQuote.value = 0
    availableBase.value = 0
  } finally {
    balanceLoaded.value = true
  }
}

/* ================= 原有下单字段逻辑 ================= */
function resetFields() {
  price.value = ''
  quantity.value = ''
  quoteAmount.value = ''
}

function switchSide(name: string | number) {
  side.value = Number(name)
  resetFields()
}
function switchType(t: number) {
  orderType.value = t
  resetFields()
}

/** 限价模式是否显示价格/数量输入 */
const showLimit = computed(() => orderType.value === 1)
/** 市价买：输入 quoteAmount；市价卖：输入 quantity */
const showQuoteAmount = computed(() => orderType.value === 2 && isBuy.value)
const showQuantity = computed(() => orderType.value === 1 || !isBuy.value)
/** 时间策略选项：市价单仅允许 GTC/IOC/FOK（PostOnly 仅限价）；限价单全量 */
const tifOptions = computed(() =>
  showLimit.value ? TIME_IN_FORCE_OPTIONS : TIME_IN_FORCE_OPTIONS.filter((o) => o.value !== 3),
)
/** 切换类型时：若当前策略在市价下不可用（PostOnly），回退到 GTC */
watch(
  () => orderType.value,
  (t) => {
    if (t === 2 && timeInForce.value === 3) timeInForce.value = 0
  },
)

const buttonType = computed(() => (isBuy.value ? 'success' : 'danger'))
const buttonText = computed(() =>
  isBuy.value ? (orderType.value === 1 ? '买入' : '市价买入') : orderType.value === 1 ? '卖出' : '市价卖出',
)

/* ================= 下单增强 ================= */
/** 快捷比例：买→计价币可用×比例；卖→基础币可用×比例 */
const RATIOS = [25, 50, 75, 100]

function fmtHuman(n: number | null | undefined, dec: number): string {
  if (n == null || !Number.isFinite(n) || n < 0) return '--'
  return n.toFixed(Math.min(Math.max(dec, 0), 8)).replace(/\.?0+$/, '') || '0'
}

function applyRatio(pct: number) {
  const r = pct / 100
  if (isBuy.value) {
    const amount = availableQuote.value * r
    if (amount <= 0) return void ElMessage.warning('无可用的计价币余额')
    if (orderType.value === 1) {
      // 限价买：amount=可用×比例，按 price 反算 quantity
      quoteAmount.value = String(amount)
      const p = Number(price.value)
      if (p > 0) quantity.value = String(amount / p)
      else {
        quantity.value = ''
        ElMessage.warning('请先输入限价')
      }
    } else {
      // 市价买：直接填预算金额
      quoteAmount.value = String(amount)
    }
  } else {
    const qty = availableBase.value * r
    if (qty <= 0) return void ElMessage.warning('无可用的基础币余额')
    quantity.value = String(qty)
  }
}

/** 点最新价 → 填入限价 */
function fillLatestPrice() {
  if (lastPriceLong.value == null) return void ElMessage.warning('暂无最新价')
  price.value = formatLong(lastPriceLong.value, PRICE_DECIMALS, 6)
}

/** 点盘口档位价格 → 填入限价（price 传入人读字符串） */
function onOrderBookPrice(p: string) {
  price.value = p
}

/** 预估金额（计价币）：限价买/卖=price×qty；市价卖=qty×最新价 */
const estAmount = computed<number | null>(() => {
  const q = Number(quantity.value)
  if (!(q > 0)) return null
  if (showLimit.value) {
    const p = Number(price.value)
    return p > 0 ? q * p : null
  }
  const p = lastPriceHuman.value
  return p && p > 0 ? q * p : null
})
/** 预估数量（基础币）：仅市价买=quoteAmount/最新价 */
const estQty = computed<number | null>(() => {
  if (!showQuoteAmount.value) return null
  const a = Number(quoteAmount.value)
  if (!(a > 0)) return null
  const p = lastPriceHuman.value
  return p && p > 0 ? a / p : null
})

/* ================= 下单 ================= */
async function submit() {
  const userId = authStore.userInfo?.id
  if (!userId) {
    ElMessage.warning('请先登录')
    return
  }
  // 校验
  if (orderType.value === 1) {
    if (!price.value || Number(price.value) <= 0) return void ElMessage.warning('请输入有效的限价')
    if (!quantity.value || Number(quantity.value) <= 0) return void ElMessage.warning('请输入有效的数量')
  } else if (isBuy.value) {
    if (!quoteAmount.value || Number(quoteAmount.value) <= 0)
      return void ElMessage.warning('请输入有效的买入金额')
  } else if (!quantity.value || Number(quantity.value) <= 0) {
    return void ElMessage.warning('请输入有效的卖出数量')
  }

  const payload = {
    userId,
    symbol: currentSymbol.value,
    side: side.value,
    orderType: orderType.value,
    // 限价单：传 price
    price: showLimit.value ? toLong(Number(price.value), PRICE_DECIMALS) : 0,
    // 市价卖 或 限价：传 quantity；市价买不传数量
    quantity: showQuantity.value ? toLong(Number(quantity.value), baseDecimals.value) : 0,
    // 市价买：传 quoteAmount
    quoteAmount: showQuoteAmount.value
      ? toLong(Number(quoteAmount.value), quoteDecimals.value)
      : 0,
    // 时间策略：默认 GTC(0)
    timeInForce: timeInForce.value,
  }

  submitting.value = true
  try {
    const res: PlaceOrderResult = await placeOrder(payload)
    const status = res.order?.status
    if (status === 4) {
      // REJECTED：余额不足/风控等原因
      ElMessage.warning(
        `下单被拒绝：${res.order?.remark || '资金不足或风控拦截（请先充值资产）'}`,
      )
    } else if (status === 0) {
      ElMessage.success(`下单成功（挂单中） 单号 ${res.order?.orderNo || ''}`)
    } else if (status === 2) {
      ElMessage.success(`下单成功（已成交） 单号 ${res.order?.orderNo || ''}`)
    } else if (status === 1) {
      ElMessage.success(`下单成功（部分成交） 单号 ${res.order?.orderNo || ''}`)
    } else {
      ElMessage.success(`下单成功 单号 ${res.order?.orderNo || ''}`)
    }
    resetFields()
  } catch (e: any) {
    // 后端错误信息已由 http 拦截器统一弹出
    // eslint-disable-next-line no-empty
    void e
  } finally {
    submitting.value = false
  }
}

watch(
  () => currentSymbol.value,
  (n, o) => {
    resetFields()
    loadBalance()
    loadTicker()
    // ws：切交易对 → 重新订阅当前 ticker
    if (o) marketWs.unsubscribe('ticker', o)
    marketWs.subscribe('ticker', n)
  },
)

onMounted(() => {
  loadBalance()
  loadTicker()
  // ws 实时最新价
  marketWs
    .on({
      onTicker: (symbol, data) => {
        if (symbol !== currentSymbol.value) return
        if (data.lastPrice != null) lastPriceLong.value = data.lastPrice
      },
    })
    .connect()
  marketWs.subscribe('ticker', currentSymbol.value)
})

onBeforeUnmount(() => {
  marketWs.unsubscribe('ticker', currentSymbol.value)
  marketWs.close()
})
</script>

<template>
  <div class="trade-page">
    <div class="trade-grid">
      <!-- 下单面板 -->
      <el-card shadow="never" class="g-card col-order">
        <template #header>
          <div class="trade-title">
            <span>下单面板</span>
            <el-select
              v-model="currentSymbol"
              style="width: 150px"
              placeholder="选择交易对"
            >
              <el-option v-for="s in symbols" :key="s.symbol" :label="s.symbol" :value="s.symbol" />
            </el-select>
          </div>
        </template>

        <!-- 买/卖 tab（:model-value 用 String，tab-change 再转回 number，避免 string 污染） -->
        <el-tabs
          :model-value="String(side)"
          class="side-tabs"
          @tab-change="switchSide"
        >
          <el-tab-pane label="买入" name="1" />
          <el-tab-pane label="卖出" name="2" />
        </el-tabs>

        <!-- 限价/市价切换 -->
        <el-segmented
          :model-value="String(orderType)"
          :options="[
            { label: '限价', value: '1' },
            { label: '市价', value: '2' },
          ]"
          @update:model-value="switchType(Number($event))"
          class="type-seg"
        />

        <!-- 时间策略 -->
        <div class="tif-row">
          <span class="ratio-label">时间策略</span>
          <el-radio-group v-model="timeInForce" size="small" class="tif-group">
            <el-radio-button
              v-for="o in tifOptions"
              :key="o.value"
              :value="o.value"
            >
              {{ o.label }}
            </el-radio-button>
          </el-radio-group>
        </div>

        <!-- 可用余额 -->
        <div class="bal-line">
          <span class="bal-label">{{ availableLabel }}</span>
          <span class="last-price-line">
            最新价
            <button type="button" class="lp-val num" @click="fillLatestPrice">
              {{ lastPriceLong != null ? formatLong(lastPriceLong, quoteDecimals, quoteDecimals) : '--' }}
            </button>
          </span>
        </div>

        <el-form label-position="top" class="order-form">
          <!-- 限价输入 -->
          <el-form-item v-if="showLimit" :label="`限价 (${parts.quote})`">
            <el-input v-model="price" placeholder="请输入限价" />
          </el-form-item>

          <!-- 数量输入：限价单 & 市价卖单 -->
          <el-form-item v-if="showQuantity" :label="`数量 (${parts.base})`">
            <el-input v-model="quantity" placeholder="请输入数量" />
          </el-form-item>

          <!-- 市价买单：输入金额 -->
          <el-form-item v-if="showQuoteAmount" :label="`买入金额 (${parts.quote})`">
            <el-input v-model="quoteAmount" placeholder="请输入预算金额" />
          </el-form-item>
        </el-form>

        <!-- 快捷比例 -->
        <div class="ratio-row">
          <span class="ratio-label">比例</span>
          <el-button
            v-for="r in RATIOS"
            :key="r"
            size="small"
            class="ratio-btn"
            @click="applyRatio(r)"
          >
            {{ r }}%
          </el-button>
        </div>

        <!-- 数量⇄金额联动预估 -->
        <div class="est-line">
          <span v-if="estAmount != null">
            预估金额 ≈ <b class="num">{{ fmtHuman(estAmount, quoteDecimals) }}</b> {{ parts.quote }}
          </span>
          <span v-if="estQty != null">
            预估数量 ≈ <b class="num">{{ fmtHuman(estQty, baseDecimals) }}</b> {{ parts.base }}
          </span>
        </div>

        <el-button
          :type="buttonType"
          size="large"
          style="width: 100%"
          :loading="submitting"
          @click="submit"
        >
          {{ buttonText }}
        </el-button>

        <el-alert
          type="info"
          :closable="false"
          show-icon
          class="balance-tip"
          title="下单前请确保账户有足够资产余额"
          description="余额不足或风控拦截时，后端会返回拒绝原因（请先到「资产」模块充值）。点击盘口或最新价可填入限价，金额自动按最小单位换算。"
        />
      </el-card>

      <!-- 深度盘口 -->
      <el-card shadow="never" class="g-card col-book">
        <OrderBookPanel
          :symbol="currentSymbol"
          :quote-symbol="parts.quote"
          :quote-decimals="quoteDecimals"
          :base-decimals="baseDecimals"
          :price-decimals="PRICE_DECIMALS"
          :last-price="lastPriceHuman"
          @select-price="onOrderBookPrice"
        />
      </el-card>

      <!-- 最近成交 -->
      <el-card shadow="never" class="g-card col-trades">
        <RecentTrades
          :symbol="currentSymbol"
          :quote-symbol="parts.quote"
          :quote-decimals="quoteDecimals"
          :base-decimals="baseDecimals"
          :price-decimals="PRICE_DECIMALS"
        />
      </el-card>
    </div>
  </div>
</template>

<style scoped>
.trade-page {
  padding: 16px;
}
.trade-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 16px;
  max-width: 1200px;
  margin: 0 auto;
}
.trade-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
}
.type-seg {
  margin: 12px 0 16px;
}
.side-tabs :deep(.el-tabs__item) {
  font-size: 16px;
  font-weight: 600;
}
.balance-tip {
  margin-top: 16px;
}
/* 可用余额 + 最新价一行 */
.bal-line {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  font-size: 13px;
  color: var(--text-secondary);
}
.lp-val {
  background: transparent;
  border: none;
  color: var(--text-primary);
  cursor: pointer;
  font-weight: 700;
  padding: 0 4px;
  text-decoration: underline;
  text-decoration-color: var(--glass-border);
  text-underline-offset: 3px;
}
.lp-val:hover {
  color: var(--accent-cyan);
}
/* 快捷比例 */
.ratio-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 12px 0 8px;
}
.ratio-label {
  font-size: 13px;
  color: var(--text-secondary);
  white-space: nowrap;
}
.ratio-btn {
  flex: 1;
}
/* 时间策略 */
.tif-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 12px 0 8px;
}
.tif-group {
  flex: 1;
  display: flex;
}
.tif-group :deep(.el-radio-button__inner) {
  width: 100%;
  font-size: 12px;
}
/* 预估联动 */
.est-line {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-height: 16px;
  margin-bottom: 12px;
  font-size: 12px;
  color: var(--text-dim);
}
.est-line b {
  color: var(--text-primary);
}

/* 宽屏三栏 */
@media (min-width: 1200px) {
  .trade-grid {
    grid-template-columns: 340px 1fr 1fr;
    align-items: stretch;
  }
  .col-book,
  .col-trades {
    min-width: 0;
  }
}
@media (min-width: 900px) and (max-width: 1199px) {
  .trade-grid {
    grid-template-columns: 340px 1fr;
  }
  .col-trades {
    grid-column: 1 / -1;
  }
}

/* 数字输入等宽对齐 */
.col-order :deep(.el-input__inner) {
  font-family: var(--font-num);
  font-variant-numeric: tabular-nums;
}
</style>
