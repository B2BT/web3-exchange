<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import * as futuresApi from '@/api/futures'
import { formatAdaptivePrice, formatLong } from '@/utils/format'

const { t } = useI18n()
const authStore = useAuthStore()
const userId = computed(() => authStore.userInfo?.id)

const symbols = ref<futuresApi.SwapContract[]>([])
const currentSymbol = ref('BTC-USDT-SWAP')
const mark = ref<string | number>(0)
const account = ref<futuresApi.FuturesAccount>({})
const positions = ref<futuresApi.FuturesPosition[]>([])

let refreshTimer: ReturnType<typeof setInterval> | undefined

const side = ref(1) // 1开多 2开空 3平多 4平空
const orderType = ref(1)
const leverage = ref(10)
const price = ref('')
const quantity = ref('')
const loading = ref(false)
const depositAmount = ref('100')

const maxLev = computed(() => {
  const c = symbols.value.find((x) => x.symbol === currentSymbol.value)
  return c?.maxLeverage ?? 100
})

const sideLabel = computed(() =>
  side.value === 1 ? t('futures.openLong') : side.value === 2 ? t('futures.openShort') : side.value === 3 ? t('futures.closeLong') : t('futures.closeShort'),
)
const sideBtnClass = computed(() => (side.value === 1 || side.value === 3 ? 'buy' : 'sell'))

async function loadAll() {
  const list = await futuresApi.contracts()
  symbols.value = list ?? []
  await Promise.all([loadMark(), loadAccount(), loadPositions()])
}

async function loadMark() {
  try { mark.value = await futuresApi.markPrice(currentSymbol.value) } catch { mark.value = 0 }
}

async function loadAccount() {
  if (!userId.value) return
  account.value = await futuresApi.account(userId.value)
}

async function loadPositions() {
  if (!userId.value) return
  positions.value = await futuresApi.positions(userId.value)
}

async function submit() {
  if (!userId.value) return
  loading.value = true
  try {
    const res = await futuresApi.placeOrder({
      userId: userId.value,
      symbol: currentSymbol.value,
      side: side.value,
      orderType: orderType.value,
      price: orderType.value === 1 ? price.value : undefined,
      quantity: quantity.value,
      leverage: leverage.value,
      marginMode: 1,
    })
    ElMessage.success(`${sideLabel.value} 下单成功: ${res?.orderNo}`)
    price.value = ''
    quantity.value = ''
    await Promise.all([loadAccount(), loadPositions()])
  } catch (e: any) {
    ElMessage.error(e?.message || '下单失败')
  } finally {
    loading.value = false
  }
}

async function deposit() {
  if (!userId.value) return
  try {
    await futuresApi.futuresDeposit(userId.value, depositAmount.value)
    ElMessage.success('入金成功')
    await loadAccount()
  } catch (e: any) {
    ElMessage.error(e?.message || '入金失败')
  }
}

onMounted(() => {
  loadAll()
  // 准实时刷新：每 5s 刷新标记价/账户/持仓，跟随 Binance 真实现货行情
  refreshTimer = setInterval(() => {
    loadMark()
    loadAccount()
    loadPositions()
  }, 5000)
})

onBeforeUnmount(() => {
  if (refreshTimer) clearInterval(refreshTimer)
})
</script>

<template>
  <div class="page-container">
    <div class="futures-grid">
      <!-- 下单面板 -->
      <el-card shadow="never" class="g-card order-card">
        <template #header>
          <div class="card-header">
            <span>{{ currentSymbol }} {{ t('futures.title') }}</span>
            <el-select v-model="currentSymbol" size="small" style="width: 180px" @change="loadAll">
              <el-option v-for="s in symbols" :key="s.symbol" :label="s.symbol" :value="s.symbol" />
            </el-select>
          </div>
        </template>

        <div class="mark-line">
          <span class="m-label">{{ t('futures.markPrice') }}</span>
          <span class="m-value num">{{ formatAdaptivePrice(Number(mark) / 1e8) }}</span>
        </div>

        <el-segmented
          :model-value="String(side)"
          :options="[
            { label: t('futures.openLong'), value: '1' },
            { label: t('futures.openShort'), value: '2' },
            { label: t('futures.closeLong'), value: '3' },
            { label: t('futures.closeShort'), value: '4' },
          ]"
          @update:model-value="side = Number($event)"
          class="side-seg"
        />

        <div class="lever-line">
          <span class="m-label">{{ t('futures.leverage') }}</span>
          <el-slider v-model="leverage" :min="1" :max="maxLev" :step="1" show-input style="flex: 1" />
        </div>

        <el-form label-position="top" class="order-form">
          <el-form-item v-if="orderType === 1" :label="t('futures.price')">
            <el-input v-model="price" :placeholder="t('futures.price')" />
          </el-form-item>
          <el-form-item :label="t('futures.quantity')">
            <el-input v-model="quantity" :placeholder="t('futures.quantity')" />
          </el-form-item>
        </el-form>

        <el-button type="primary" class="submit-btn" :class="sideBtnClass" :loading="loading" @click="submit">
          {{ sideLabel }}
        </el-button>
      </el-card>

      <!-- 账户 + 持仓 -->
      <el-card shadow="never" class="g-card acct-card">
        <template #header>
          <div class="card-header">
            <span>{{ t('futures.account') }}</span>
            <div class="deposit-row">
              <el-input v-model="depositAmount" size="small" style="width: 90px" />
              <el-button size="small" type="success" @click="deposit">入金</el-button>
            </div>
          </div>
        </template>

        <div class="acct-row">
          <div class="acct-item"><span class="m-label">{{ t('futures.marginBalance') }}</span><b class="num">{{ formatLong(account.marginBalance, 8) }}</b></div>
          <div class="acct-item"><span class="m-label">{{ t('futures.available') }}</span><b class="num">{{ formatLong(account.availableBalance, 8) }}</b></div>
          <div class="acct-item"><span class="m-label">{{ t('futures.positionMargin') }}</span><b class="num">{{ formatLong(account.positionMargin, 8) }}</b></div>
          <div class="acct-item"><span class="m-label">{{ t('futures.realizedPnl') }}</span><b class="num" :class="Number(account.realizedPnl) >= 0 ? 'pos' : 'neg'">{{ formatLong(account.realizedPnl, 8) }}</b></div>
        </div>

        <el-table :data="positions" stripe style="margin-top: 8px">
          <el-table-column :label="t('futures.symbol')" prop="symbol" width="150" />
          <el-table-column :label="t('futures.side')">
            <template #default="{ row }">
              <el-tag :type="row.side === 1 ? 'success' : 'danger'" size="small">{{ row.side === 1 ? t('futures.long') : t('futures.short') }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="t('futures.size')">
            <template #default="{ row }">{{ formatLong(row.size, 8) }}</template>
          </el-table-column>
          <el-table-column :label="t('futures.entryPrice')">
            <template #default="{ row }">{{ formatLong(row.entryPrice, 8) }}</template>
          </el-table-column>
          <el-table-column :label="t('futures.unrealizedPnl')">
            <template #default="{ row }">
              <span class="num" :class="Number(row.unrealizedPnl) >= 0 ? 'pos' : 'neg'">{{ formatLong(row.unrealizedPnl, 8) }}</span>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </div>
  </div>
</template>

<style scoped>
.futures-grid {
  display: grid;
  grid-template-columns: 420px 1fr;
  gap: 16px;
}
@media (max-width: 900px) {
  .futures-grid { grid-template-columns: 1fr; }
}
.card-header { display: flex; align-items: center; justify-content: space-between; }
.mark-line { display: flex; gap: 8px; align-items: baseline; padding: 4px 0 12px; }
.m-label { color: var(--text-secondary); font-size: 13px; }
.m-value { font-size: 20px; font-weight: 700; color: var(--accent, #3b82f6); }
.side-seg { width: 100%; margin-bottom: 12px; }
.lever-line { display: flex; align-items: center; gap: 10px; margin-bottom: 8px; }
.submit-btn { width: 100%; margin-top: 4px; }
.submit-btn.buy { background: #16a34a; border-color: #16a34a; }
.submit-btn.sell { background: #ef4444; border-color: #ef4444; }
.acct-row { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; }
.acct-item { display: flex; flex-direction: column; gap: 4px; }
.pos { color: #22c55e; }
.neg { color: #ef4444; }
.deposit-row { display: flex; gap: 6px; align-items: center; }
</style>
