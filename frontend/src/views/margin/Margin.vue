<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import * as marginApi from '@/api/margin'
import type { MarginAccount, MarginLoan } from '@/api/margin'
import { formatLong, toLong } from '@/utils/format'
import { coinDecimals } from '@/config/market'

const authStore = useAuthStore()
const userId = computed(() => authStore.userInfo?.id)

const COINS = ['USDT', 'BTC', 'ETH'] as const
const SYMBOL = ref<'USDT' | 'BTC' | 'ETH'>('USDT')

const account = ref<MarginAccount | null>(null)
const accountLoading = ref(false)
const loans = ref<MarginLoan[]>([])
const loansTotal = ref(0)
const loansLoading = ref(false)

/** 最小单位 → 人读 */
function human(v: number | undefined, symbol: string): string {
  return formatLong(v ?? 0, coinDecimals(symbol), 6)
}

async function loadAccount() {
  if (!userId.value) return
  accountLoading.value = true
  try {
    account.value = await marginApi.marginAccount(userId.value, SYMBOL.value)
  } catch {
    account.value = null
  } finally {
    accountLoading.value = false
  }
}

async function loadLoans() {
  if (!userId.value) return
  loansLoading.value = true
  try {
    const d = await marginApi.marginLoans(userId.value, 1, 50)
    loans.value = d?.records ?? []
    loansTotal.value = d?.total ?? 0
  } finally {
    loansLoading.value = false
  }
}

/** 开户 + 刷新 */
async function openAccount() {
  if (!userId.value) return
  accountLoading.value = true
  try {
    await marginApi.marginOpen(userId.value, SYMBOL.value)
    await loadAccount()
    ElMessage.success('开户成功')
  } finally {
    accountLoading.value = false
  }
}

async function refresh() {
  await loadAccount()
  await loadLoans()
}

// ---------- 表单 ----------
const transferRef = ref<FormInstance>()
const borrowRef = ref<FormInstance>()
const repayRef = ref<FormInstance>()

const transferForm = reactive({ amount: '', dir: 'in' as 'in' | 'out' })
const borrowForm = reactive({ amount: '' })
const repayForm = reactive({ amount: '' })

const transferRules: FormRules = { amount: [{ required: true, message: '请输入金额', trigger: 'blur' }] }
const borrowRules: FormRules = { amount: [{ required: true, message: '请输入金额', trigger: 'blur' }] }
const repayRules: FormRules = { amount: [{ required: true, message: '请输入金额', trigger: 'blur' }] }

const opLoading = ref(false)

async function submitTransfer() {
  if (!userId.value) return
  const amount = toLong(Number(transferForm.amount || 0), coinDecimals(SYMBOL.value))
  if (amount <= 0) { ElMessage.warning('金额需大于 0'); return }
  opLoading.value = true
  try {
    if (transferForm.dir === 'in') await marginApi.marginTransferIn(userId.value, SYMBOL.value, amount)
    else await marginApi.marginTransferOut(userId.value, SYMBOL.value, amount)
    ElMessage.success('划转成功')
    transferForm.amount = ''
    await loadAccount()
  } finally { opLoading.value = false }
}

async function submitBorrow() {
  if (!userId.value) return
  const amount = toLong(Number(borrowForm.amount || 0), coinDecimals(SYMBOL.value))
  if (amount <= 0) { ElMessage.warning('金额需大于 0'); return }
  opLoading.value = true
  try {
    await marginApi.marginBorrow(userId.value, SYMBOL.value, amount)
    ElMessage.success('借币成功')
    borrowForm.amount = ''
    await loadAccount()
    await loadLoans()
  } finally { opLoading.value = false }
}

async function submitRepay() {
  if (!userId.value) return
  const amount = toLong(Number(repayForm.amount || 0), coinDecimals(SYMBOL.value))
  if (amount <= 0) { ElMessage.warning('金额需大于 0'); return }
  opLoading.value = true
  try {
    await marginApi.marginRepay(userId.value, SYMBOL.value, amount)
    ElMessage.success('还币成功')
    repayForm.amount = ''
    await loadAccount()
    await loadLoans()
  } finally { opLoading.value = false }
}

function riskClass(r: number | null | undefined): string {
  if (r == null) return 'ov-total'
  if (r < 120) return 'risk-danger'
  if (r < 150) return 'risk-warn'
  return 'risk-safe'
}

onMounted(() => {
  loadAccount()
  loadLoans()
})
</script>

<template>
  <div class="page-container">
    <el-card shadow="never" class="g-card">
      <template #header>
        <div class="card-header">
          <span>杠杆现货 Margin</span>
          <div class="header-right">
            <el-select v-model="SYMBOL" style="width: 110px" @change="refresh">
              <el-option v-for="c in COINS" :key="c" :label="c" :value="c" />
            </el-select>
            <el-button size="small" :loading="accountLoading" @click="refresh">刷新</el-button>
          </div>
        </div>
      </template>

      <!-- 账户卡片 -->
      <el-row :gutter="16" v-loading="accountLoading" style="margin-bottom: 20px">
        <el-col :span="6">
          <el-card shadow="never" class="g-card stat-card tilt3d">
            <div class="stat-label">抵押 Collateral</div>
            <div class="stat-value num">{{ human(account?.collateral, SYMBOL) }} {{ SYMBOL }}</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="never" class="g-card stat-card">
            <div class="stat-label">借入 Borrowed</div>
            <div class="stat-value num">{{ human(account?.borrowed, SYMBOL) }} {{ SYMBOL }}</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="never" class="g-card stat-card">
            <div class="stat-label">未还利息</div>
            <div class="stat-value num">{{ human(account?.interestAccrued, SYMBOL) }} {{ SYMBOL }}</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="never" class="g-card stat-card">
            <div class="stat-label">风险率 Risk</div>
            <div class="stat-value num" :class="riskClass(account?.riskRate)">
              {{ account?.riskRate == null ? '—' : account.riskRate + '%' }}
            </div>
          </el-card>
        </el-col>
      </el-row>

      <el-alert
        v-if="!account"
        type="info"
        :closable="false"
        title="尚未开户，请先开户后再操作"
        style="margin-bottom: 16px"
      />

      <!-- 操作区 -->
      <el-row :gutter="16">
        <el-col :span="8">
          <el-card shadow="never" class="g-card op-card">
            <template #header><span>划转（现货 ⇄ 杠杆）</span></template>
            <el-form ref="transferRef" :model="transferForm" :rules="transferRules" label-width="70px">
              <el-form-item label="方向">
                <el-radio-group v-model="transferForm.dir">
                  <el-radio-button value="in">入金</el-radio-button>
                  <el-radio-button value="out">出金</el-radio-button>
                </el-radio-group>
              </el-form-item>
              <el-form-item label="金额">
                <el-input v-model="transferForm.amount" placeholder="请输入金额" clearable />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" :loading="opLoading" @click="submitTransfer">确认划转</el-button>
              </el-form-item>
            </el-form>
          </el-card>
        </el-col>
        <el-col :span="8">
          <el-card shadow="never" class="g-card op-card">
            <template #header><span>借币</span></template>
            <el-form ref="borrowRef" :model="borrowForm" :rules="borrowRules" label-width="70px">
              <el-form-item label="金额">
                <el-input v-model="borrowForm.amount" placeholder="请输入借入金额" clearable />
              </el-form-item>
              <el-form-item>
                <el-button type="warning" :loading="opLoading" @click="submitBorrow">借币</el-button>
              </el-form-item>
            </el-form>
          </el-card>
        </el-col>
        <el-col :span="8">
          <el-card shadow="never" class="g-card op-card">
            <template #header><span>还币（本金 + 利息）</span></template>
            <el-form ref="repayRef" :model="repayForm" :rules="repayRules" label-width="70px">
              <el-form-item label="金额">
                <el-input v-model="repayForm.amount" placeholder="请输入还款金额" clearable />
              </el-form-item>
              <el-form-item>
                <el-button type="danger" :loading="opLoading" @click="submitRepay">还币</el-button>
              </el-form-item>
            </el-form>
          </el-card>
        </el-col>
      </el-row>

      <!-- 借币记录 -->
      <h4 class="section-title">借币记录</h4>
      <el-table v-loading="loansLoading" :data="loans" stripe>
        <el-table-column prop="symbol" label="币种" width="90" />
        <el-table-column label="借入本金" width="160">
          <template #default="{ row }"><span class="num">{{ human(row.amount, row.symbol) }} {{ row.symbol }}</span></template>
        </el-table-column>
        <el-table-column label="剩余本金" width="160">
          <template #default="{ row }"><span class="num">{{ human(row.principalRemain, row.symbol) }} {{ row.symbol }}</span></template>
        </el-table-column>
        <el-table-column label="累计利息" width="160">
          <template #default="{ row }"><span class="num">{{ human(row.interestAccrued, row.symbol) }} {{ row.symbol }}</span></template>
        </el-table-column>
        <el-table-column label="日利率" width="110">
          <template #default="{ row }">{{ ((row.rateDaily ?? 0) / 10000 * 100).toFixed(3) }}%</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 0 ? 'warning' : 'success'" size="small">
              {{ row.status === 0 ? '借出中' : '已还清' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="openTime" label="借出时间" min-width="160" />
      </el-table>
      <el-empty v-if="!loansLoading && !loans.length" description="暂无借币记录" style="padding: 16px 0" />
    </el-card>
  </div>
</template>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.header-right {
  display: flex;
  gap: 8px;
  align-items: center;
}
.stat-card {
  text-align: center;
  padding: 8px 0;
}
.stat-label {
  font-size: 12px;
  color: var(--text-secondary);
  margin-bottom: 8px;
}
.stat-value {
  font-size: 22px;
  font-weight: 700;
}
.risk-danger { color: #f56c6c; }
.risk-warn { color: #e6a23c; }
.risk-safe { color: #67c23a; }
.section-title {
  margin: 24px 0 12px;
  color: var(--text-primary);
}
.op-card {
  min-height: 220px;
}
</style>
