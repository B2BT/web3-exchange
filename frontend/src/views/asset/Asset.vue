<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import QRCode from 'qrcode'
import { useAuthStore } from '@/stores/auth'
import * as assetApi from '@/api/asset'
import * as chainApi from '@/api/chain'
import type { AccountItem, LedgerItem } from '@/api/asset'
import type { AssetAddress } from '@/api/chain'
import { formatLong } from '@/utils/format'
import { coinDecimals } from '@/config/market'

const authStore = useAuthStore()

const userId = computed(() => authStore.userInfo?.id)

/** bizType 中文映射 */
const BIZ_TYPE_MAP: Record<string, string> = {
  FREEZE: '冻结',
  UNFREEZE: '解冻',
  TRANSFER_IN: '转入',
  TRANSFER_OUT: '转出',
  DEPOSIT: '充值',
  WITHDRAW: '提现',
  FEE: '手续费',
  REBATE: '返佣',
}

/** direction 中文映射：1=入 2=出 3=冻结 4=解冻 5=冻结转出 */
const DIRECTION_MAP: Record<number, string> = {
  1: '入',
  2: '出',
  3: '冻结',
  4: '解冻',
  5: '冻结转出',
}

/** direction → 颜色类型（入=绿 出=红 冻结=橙） */
const DIRECTION_COLOR: Record<number, 'green' | 'red' | 'orange'> = {
  1: 'green',
  2: 'red',
  3: 'orange',
  4: 'green',
  5: 'orange',
}

/** 支持的币种（decimals 用于 Long 最小单位换算） */
const COINS = ['BTC', 'ETH', 'USDT'] as const
/** 币种 → 可选的链 */
const COIN_CHAINS: Record<string, string[]> = {
  BTC: ['BTC'],
  ETH: ['ETH'],
  USDT: ['ETH', 'TRON'],
}

// ---------- 资产总览 ----------
const accounts = ref<AccountItem[]>([])
const overviewLoading = ref(false)

async function loadAccounts() {
  if (!userId.value) return
  overviewLoading.value = true
  try {
    accounts.value = await assetApi.accounts(userId.value)
  } finally {
    overviewLoading.value = false
  }
}

// ---------- 充值地址 ----------
const dep = reactive({ symbol: 'USDT' as string, chainCode: 'TRON' as string })
const depAddress = ref<AssetAddress | null>(null)
const depLoading = ref(false)
const depQr = ref('') // 二维码 dataURL

async function queryDepositAddress() {
  if (!userId.value) return
  if (!dep.chainCode || !dep.symbol) {
    ElMessage.warning('请选择币种与链')
    return
  }
  depLoading.value = true
  depAddress.value = null
  depQr.value = ''
  try {
    const addr = await chainApi.depositAddress(userId.value, dep.chainCode, dep.symbol)
    depAddress.value = addr
    if (addr?.address) {
      try {
        depQr.value = await QRCode.toDataURL(addr.address, { width: 180, margin: 1 })
      } catch {
        depQr.value = ''
      }
    }
  } catch {
    depAddress.value = null
  } finally {
    depLoading.value = false
  }
}

function copyAddress() {
  if (!depAddress.value?.address) return
  navigator.clipboard
    .writeText(depAddress.value.address)
    .then(() => ElMessage.success('地址已复制'))
    .catch(() => ElMessage.warning('复制失败，请手动复制'))
}

// ---------- 提现 ----------
const wdRef = ref<FormInstance>()
const wdLoading = ref(false)
const wdForm = reactive({
  symbol: 'USDT' as string,
  chainCode: 'TRON' as string,
  toAddress: '',
  amount: '', // 人读金额（小数）
})

const wdRules: FormRules = {
  symbol: [{ required: true, message: '请选择币种', trigger: 'change' }],
  chainCode: [{ required: true, message: '请选择链', trigger: 'change' }],
  toAddress: [
    { required: true, message: '请输入提现目标地址', trigger: 'blur' },
    { min: 20, message: '地址长度过短', trigger: 'blur' },
  ],
  amount: [
    { required: true, message: '请输入提现金额', trigger: 'blur' },
    {
      validator: (_r, v, cb) =>
        !v || Number(v) > 0 ? cb() : cb(new Error('金额需大于 0')),
      trigger: 'blur',
    },
  ],
}

/** 人读金额 → Long 最小单位（按币种精度） */
function toMinUnit(amount: string, decimals: number): number {
  const s = amount.trim()
  const n = Number(s)
  if (!s || !Number.isFinite(n) || n < 0) return 0
  const [intPart, fracPart = ''] = s.split('.')
  const frac = fracPart.padEnd(decimals, '0').slice(0, decimals)
  return Number(intPart + frac)
}

async function submitWithdraw() {
  if (!wdRef.value || !userId.value) return
  const valid = await wdRef.value.validate().catch(() => false)
  if (!valid) return
  wdLoading.value = true
  try {
    const result = await chainApi.withdrawApply({
      userId: userId.value,
      symbol: wdForm.symbol,
      chainCode: wdForm.chainCode,
      toAddress: wdForm.toAddress,
      amount: toMinUnit(wdForm.amount, coinDecimals(wdForm.symbol)),
    })
    ElMessage.success(`提现申请已提交，单号 ${result.requestId || result.id || ''}`)
    wdForm.toAddress = ''
    wdForm.amount = ''
  } finally {
    wdLoading.value = false
  }
}

function onSymbolChange(target: { symbol?: string; chainCode?: string }) {
  const chains = COIN_CHAINS[target.symbol || '']
  if (chains?.length) target.chainCode = chains[0]
}

// ---------- 资金明细 ----------
const ledgerLoading = ref(false)
const ledgerRecords = ref<LedgerItem[]>([])
const ledgerQuery = reactive({ page: 1, size: 20 })
const ledgerTotal = ref(0)

async function loadLedger() {
  if (!userId.value) return
  ledgerLoading.value = true
  try {
    const data = await assetApi.ledgerList({
      userId: userId.value,
      page: ledgerQuery.page,
      size: ledgerQuery.size,
    })
    ledgerRecords.value = data?.records ?? []
    ledgerTotal.value = data?.total ?? 0
  } finally {
    ledgerLoading.value = false
  }
}

function onLedgerPageChange(page: number) {
  ledgerQuery.page = page
  loadLedger()
}

function onLedgerSizeChange(size: number) {
  ledgerQuery.size = size
  ledgerQuery.page = 1
  loadLedger()
}

/** 带符号展示：入=+，出/冻结=-（精度按币种） */
function ledgerSigned(amount: number | undefined, direction: number | undefined, symbol: string | undefined): string {
  const abs = formatLong(amount ?? 0, coinDecimals(symbol || ''))
  const sign = direction === 1 || direction === 4 ? '+' : '-'
  return `${sign}${abs}`
}

onMounted(loadAccounts)

// ---------- tab 切换：切到资金明细时加载第 1 页 ----------
const activeTab = ref('overview')
const LEDGER_TAB = 'ledger'
watch(activeTab, (tab) => {
  if (tab === LEDGER_TAB) {
    ledgerQuery.page = 1
    loadLedger()
  }
})
</script>

<template>
  <div class="page-container">
    <el-card shadow="never" class="g-card">
      <template #header>
        <div class="card-header">
          <span>资产</span>
          <el-button size="small" :loading="overviewLoading" @click="loadAccounts">刷新</el-button>
        </div>
      </template>

      <el-tabs v-model="activeTab">
        <!-- 资产总览 -->
        <el-tab-pane label="资产总览" name="overview" lazy>
          <el-table v-loading="overviewLoading" :data="accounts" stripe>
            <el-table-column prop="symbol" label="币种" width="140" />
            <el-table-column label="可用余额">
              <template #default="{ row }">
                <span class="num">{{ formatLong(row.available, coinDecimals(row.symbol)) }} {{ row.symbol }}</span>
              </template>
            </el-table-column>
            <el-table-column label="冻结余额">
              <template #default="{ row }">
                <span class="num">{{ formatLong(row.frozen, coinDecimals(row.symbol)) }} {{ row.symbol }}</span>
              </template>
            </el-table-column>
            <el-table-column label="总余额">
              <template #default="{ row }">
                <b class="num">{{ formatLong(row.total, coinDecimals(row.symbol)) }}</b> <span class="num">{{ row.symbol }}</span>
              </template>
            </el-table-column>
            <el-table-column label="账户状态" width="120">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
                  {{ row.status === 1 ? '正常' : row.status === 2 ? '冻结' : '禁用' }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!overviewLoading && !accounts.length" description="暂无资产账户" />
        </el-tab-pane>

        <!-- 充值 -->
        <el-tab-pane label="充值" lazy>
          <el-form label-width="80px" style="max-width: 520px">
            <el-form-item label="币种">
              <el-select v-model="dep.symbol" @change="() => onSymbolChange(dep)">
                <el-option v-for="c in COINS" :key="c" :label="c" :value="c" />
              </el-select>
            </el-form-item>
            <el-form-item label="链">
              <el-select v-model="dep.chainCode">
                <el-option
                  v-for="cc in COIN_CHAINS[dep.symbol] || []"
                  :key="cc"
                  :label="cc"
                  :value="cc"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="操作">
              <el-button type="primary" :loading="depLoading" @click="queryDepositAddress">
                {{ depAddress ? '刷新充值地址' : '生成充值地址' }}
              </el-button>
            </el-form-item>
          </el-form>

          <el-alert
            v-if="!depAddress && !depLoading"
            type="info"
            :closable="false"
            title="选择币种与链后点击「生成充值地址」，系统自动派生该用户的 BIP44 链上地址"
            style="max-width: 520px"
          />

          <div v-if="depAddress" class="dep-addr-box">
            <div class="dep-qr">
              <img v-if="depQr" :src="depQr" alt="充值地址二维码" width="180" height="180" />
            </div>
            <el-descriptions :column="1" border style="flex: 1; min-width: 280px">
              <el-descriptions-item label="链">{{ depAddress.chainCode }}</el-descriptions-item>
              <el-descriptions-item label="币种">{{ depAddress.symbol }}</el-descriptions-item>
              <el-descriptions-item label="充币地址">
                <span class="addr-text">{{ depAddress.address }}</span>
                <el-button link type="primary" size="small" @click="copyAddress">复制</el-button>
              </el-descriptions-item>
              <el-descriptions-item v-if="depAddress.memo" label="Memo/Tag">
                {{ depAddress.memo }}
              </el-descriptions-item>
              <el-descriptions-item>
                <span class="dep-tip">提示：仅支持充值对应链与币种，充错地址资产可能无法找回</span>
              </el-descriptions-item>
            </el-descriptions>
          </div>
        </el-tab-pane>

        <!-- 提现 -->
        <el-tab-pane label="提现" lazy>
          <el-form
            ref="wdRef"
            :model="wdForm"
            :rules="wdRules"
            label-width="80px"
            style="max-width: 520px"
          >
            <el-form-item label="币种" prop="symbol">
              <el-select v-model="wdForm.symbol" @change="() => onSymbolChange(wdForm)">
                <el-option v-for="c in COINS" :key="c" :label="c" :value="c" />
              </el-select>
            </el-form-item>
            <el-form-item label="链" prop="chainCode">
              <el-select v-model="wdForm.chainCode">
                <el-option
                  v-for="cc in COIN_CHAINS[wdForm.symbol] || []"
                  :key="cc"
                  :label="cc"
                  :value="cc"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="目标地址" prop="toAddress">
              <el-input v-model="wdForm.toAddress" placeholder="收款地址" clearable />
            </el-form-item>
            <el-form-item label="金额" prop="amount">
              <el-input v-model="wdForm.amount" placeholder="请输入提现金额" clearable />
              <span class="unit-hint">单位 {{ wdForm.symbol }}（精度 {{ coinDecimals(wdForm.symbol) }}）</span>
            </el-form-item>
            <el-form-item>
              <el-button type="danger" :loading="wdLoading" @click="submitWithdraw">提交提现申请</el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <!-- 资金明细 -->
        <el-tab-pane label="资金明细" name="ledger" lazy>
          <el-table v-loading="ledgerLoading" :data="ledgerRecords" stripe>
            <el-table-column prop="createTime" label="时间" min-width="160" />
            <el-table-column label="币种" width="90">
              <template #default="{ row }">{{ row.symbol }}</template>
            </el-table-column>
            <el-table-column label="业务类型" width="100">
              <template #default="{ row }">{{ BIZ_TYPE_MAP[row.bizType] || row.bizType || '-' }}</template>
            </el-table-column>
            <el-table-column label="方向" width="100">
              <template #default="{ row }">
                <span :class="`dir-${DIRECTION_COLOR[row.direction] || 'red'}`">
                  {{ DIRECTION_MAP[row.direction] || row.direction || '-' }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="金额" min-width="140">
              <template #default="{ row }">
                <span :class="`dir-${DIRECTION_COLOR[row.direction] || 'red'}`">
                  {{ ledgerSigned(row.amount, row.direction, row.symbol) }}
                </span>
                <span class="num"> {{ row.symbol }}</span>
              </template>
            </el-table-column>
            <el-table-column label="变动后可用余额" min-width="160">
              <template #default="{ row }">
                <span class="num">
                  {{ formatLong(row.afterAvailable, coinDecimals(row.symbol || '')) }} {{ row.symbol }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="备注" min-width="120">
              <template #default="{ row }">{{ row.remark || '-' }}</template>
            </el-table-column>
          </el-table>

          <el-empty
            v-if="!ledgerLoading && !ledgerRecords.length"
            description="暂无资金明细"
            style="padding: 32px 0"
          />

          <div v-if="ledgerTotal > 0" class="ledger-pager">
            <el-pagination
              background
              layout="total, sizes, prev, pager, next"
              :total="ledgerTotal"
              :current-page="ledgerQuery.page"
              :page-size="ledgerQuery.size"
              :page-sizes="[10, 20, 50, 100]"
              @current-change="onLedgerPageChange"
              @size-change="onLedgerSizeChange"
            />
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.addr-text {
  word-break: break-all;
  margin-right: 8px;
}
.dep-addr-box {
  display: flex;
  gap: 24px;
  align-items: flex-start;
  max-width: 720px;
}
.dep-qr {
  padding: 8px;
  background: #fff;
  border-radius: 8px;
  flex-shrink: 0;
}
.dep-tip {
  font-size: 12px;
  color: #e6a23c;
}
.unit-hint {
  margin-left: 10px;
  font-size: 12px;
  color: var(--text-dim);
}
.ledger-pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
.dir-green {
  color: #67c23a;
  font-weight: 600;
}
.dir-red {
  color: #f56c6c;
  font-weight: 600;
}
.dir-orange {
  color: #e6a23c;
  font-weight: 600;
}
</style>
