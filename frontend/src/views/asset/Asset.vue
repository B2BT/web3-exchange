<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import * as assetApi from '@/api/asset'
import * as chainApi from '@/api/chain'
import type { AccountItem } from '@/api/asset'
import type { AssetAddress } from '@/api/chain'
import { formatLong } from '@/utils/format'
import { coinDecimals } from '@/config/market'

const authStore = useAuthStore()

const userId = computed(() => authStore.userInfo?.id)

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

async function queryDepositAddress() {
  if (!userId.value) return
  if (!dep.chainCode || !dep.symbol) {
    ElMessage.warning('请选择币种与链')
    return
  }
  depLoading.value = true
  depAddress.value = null
  try {
    depAddress.value = await chainApi.depositAddress(userId.value, dep.chainCode, dep.symbol)
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

onMounted(loadAccounts)
</script>

<template>
  <div class="page-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>资产</span>
          <el-button size="small" :loading="overviewLoading" @click="loadAccounts">刷新</el-button>
        </div>
      </template>

      <el-tabs>
        <!-- 资产总览 -->
        <el-tab-pane label="资产总览" lazy>
          <el-table v-loading="overviewLoading" :data="accounts" stripe>
            <el-table-column prop="symbol" label="币种" width="140" />
            <el-table-column label="可用余额">
              <template #default="{ row }">
                {{ formatLong(row.available, coinDecimals(row.symbol)) }} {{ row.symbol }}
              </template>
            </el-table-column>
            <el-table-column label="冻结余额">
              <template #default="{ row }">
                {{ formatLong(row.frozen, coinDecimals(row.symbol)) }} {{ row.symbol }}
              </template>
            </el-table-column>
            <el-table-column label="总余额">
              <template #default="{ row }">
                <b>{{ formatLong(row.total, coinDecimals(row.symbol)) }}</b> {{ row.symbol }}
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
                查询充值地址
              </el-button>
            </el-form-item>
          </el-form>

          <el-alert
            v-if="!depAddress && !depLoading"
            type="info"
            :closable="false"
            title="充值地址由运营按用户+链+币种预配置；如未配置将提示「未绑定充币地址」"
            style="max-width: 520px"
          />

          <el-descriptions v-if="depAddress" :column="1" border style="max-width: 520px">
            <el-descriptions-item label="链">{{ depAddress.chainCode }}</el-descriptions-item>
            <el-descriptions-item label="币种">{{ depAddress.symbol }}</el-descriptions-item>
            <el-descriptions-item label="充币地址">
              <span class="addr-text">{{ depAddress.address }}</span>
              <el-button link type="primary" size="small" @click="copyAddress">复制</el-button>
            </el-descriptions-item>
            <el-descriptions-item v-if="depAddress.memo" label="Memo/Tag">
              {{ depAddress.memo }}
            </el-descriptions-item>
          </el-descriptions>
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
.unit-hint {
  margin-left: 10px;
  font-size: 12px;
  color: #909399;
}
</style>
