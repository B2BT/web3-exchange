<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import * as stakingApi from '@/api/staking'
import type { StakingInterest, StakingPosition, StakingProduct } from '@/api/staking'
import { formatLong, toLong } from '@/utils/format'
import { coinDecimals } from '@/config/market'

const authStore = useAuthStore()
const userId = computed(() => authStore.userInfo?.id)

const products = ref<StakingProduct[]>([])
const positions = ref<StakingPosition[]>([])
const interests = ref<StakingInterest[]>([])
const loading = ref(false)
const opLoading = ref(false)

/** 最小单位 → 人读 */
function human(v: number | undefined, symbol: string): string {
  return formatLong(v ?? 0, coinDecimals(symbol), 6)
}
function ratePct(bp: number | undefined): string {
  return (((bp ?? 0) / 10000) * 100).toFixed(2) + '%'
}

async function loadAll() {
  if (!userId.value) return
  loading.value = true
  try {
    products.value = await stakingApi.stakingProducts()
    positions.value = await stakingApi.stakingPositions(userId.value)
    const d = await stakingApi.stakingInterests(userId.value, 1, 50)
    interests.value = d?.records ?? []
  } finally {
    loading.value = false
  }
}

// ---------- 质押表单 ----------
const stakeRef = ref<FormInstance>()
const stakeForm = reactive({ productCode: '', amount: '' })
const stakeRules: FormRules = {
  productCode: [{ required: true, message: '请选择产品', trigger: 'change' }],
  amount: [{ required: true, message: '请输入金额', trigger: 'blur' }],
}

async function submitStake() {
  if (!userId.value) return
  const product = products.value.find(p => p.productCode === stakeForm.productCode)
  if (!product) return
  const amount = toLong(Number(stakeForm.amount || 0), coinDecimals(product.symbol ?? 'USDT'))
  if (amount <= 0) { ElMessage.warning('金额需大于 0'); return }
  opLoading.value = true
  try {
    await stakingApi.stakingStake(userId.value, product.productCode!, amount)
    ElMessage.success('质押成功')
    stakeForm.amount = ''
    await loadAll()
  } finally { opLoading.value = false }
}

async function doRedeem(pos: StakingPosition) {
  if (!userId.value || !pos.productCode) return
  opLoading.value = true
  try {
    await stakingApi.stakingRedeem(userId.value, pos.productCode)
    ElMessage.success('赎回成功')
    await loadAll()
  } finally { opLoading.value = false }
}

onMounted(loadAll)
</script>

<template>
  <div class="page-container">
    <el-card shadow="never" class="g-card">
      <template #header>
        <div class="card-header">
          <span>Staking / Earn 理财</span>
          <el-button size="small" :loading="loading" @click="loadAll">刷新</el-button>
        </div>
      </template>

      <!-- 质押产品 -->
      <h4 class="section-title">质押产品</h4>
      <el-row :gutter="16" v-loading="loading">
        <el-col :span="8" v-for="p in products" :key="p.id">
          <el-card shadow="never" class="g-card product-card tilt3d scan-wrap">
            <div class="p-name">{{ p.name }}</div>
            <div class="p-rate">
              <span class="rate-num">{{ ratePct(p.annualRateBp) }}</span>
              <span class="rate-label">年化</span>
            </div>
            <div class="p-meta">
              <el-tag size="small" :type="p.type === 0 ? 'success' : 'warning'">
                {{ p.type === 0 ? '活期' : '锁仓' + (p.lockDays ?? 0) + '天' }}
              </el-tag>
              <span class="p-symbol">{{ p.symbol }}</span>
              <span class="p-min">最低 {{ human(p.minAmount, p.symbol ?? 'USDT') }} {{ p.symbol }}</span>
            </div>
            <el-button type="primary" size="small" style="width: 100%; margin-top: 12px" @click="stakeForm.productCode = p.productCode!; stakeForm.amount = ''">
              质押
            </el-button>
          </el-card>
        </el-col>
      </el-row>

      <!-- 质押表单 -->
      <el-card shadow="never" class="g-card stake-card">
        <template #header><span>质押</span></template>
        <el-form ref="stakeRef" :model="stakeForm" :rules="stakeRules" label-width="70px" inline>
          <el-form-item label="产品" prop="productCode">
            <el-select v-model="stakeForm.productCode" placeholder="选择产品" style="width: 200px">
              <el-option v-for="p in products" :key="p.productCode" :label="p.name" :value="p.productCode" />
            </el-select>
          </el-form-item>
          <el-form-item label="金额" prop="amount">
            <el-input v-model="stakeForm.amount" placeholder="质押金额" style="width: 200px" clearable />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="opLoading" @click="submitStake">确认质押</el-button>
          </el-form-item>
        </el-form>
      </el-card>

      <!-- 我的持仓 -->
      <h4 class="section-title">我的持仓</h4>
      <el-table v-loading="loading" :data="positions" stripe>
        <el-table-column prop="productCode" label="产品" width="130" />
        <el-table-column label="本金" width="150">
          <template #default="{ row }"><span class="num">{{ human(row.amount, row.symbol) }} {{ row.symbol }}</span></template>
        </el-table-column>
        <el-table-column label="已结收益" width="150">
          <template #default="{ row }"><span class="num gain">+{{ human(row.totalInterest, row.symbol) }} {{ row.symbol }}</span></template>
        </el-table-column>
        <el-table-column prop="startTime" label="质押时间" min-width="170" />
        <el-table-column prop="lockEndTime" label="到期时间" min-width="170">
          <template #default="{ row }">{{ row.lockEndTime || '活期' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 0 ? 'warning' : 'success'" size="small">
              {{ row.status === 0 ? '质押中' : '已赎回' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button v-if="row.status === 0" size="small" type="danger" :loading="opLoading" @click="doRedeem(row)">赎回</el-button>
            <span v-else>—</span>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && !positions.length" description="暂无持仓" style="padding: 16px 0" />

      <!-- 收益流水 -->
      <h4 class="section-title">收益流水</h4>
      <el-table v-loading="loading" :data="interests" stripe>
        <el-table-column prop="symbol" label="币种" width="90" />
        <el-table-column label="收益" width="160">
          <template #default="{ row }"><span class="num gain">+{{ human(row.amount, row.symbol) }} {{ row.symbol }}</span></template>
        </el-table-column>
        <el-table-column prop="settleDate" label="结算日期" width="120" />
        <el-table-column prop="createTime" label="结算时间" min-width="170" />
      </el-table>
      <el-empty v-if="!loading && !interests.length" description="暂无收益记录" style="padding: 16px 0" />
    </el-card>
  </div>
</template>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.section-title {
  margin: 24px 0 12px;
  color: var(--text-primary);
}
.product-card {
  text-align: center;
  margin-bottom: 16px;
}
.p-name {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 8px;
}
.p-rate {
  margin-bottom: 8px;
}
.rate-num {
  font-size: 26px;
  font-weight: 700;
  color: #f0b90b;
  margin-right: 4px;
}
.rate-label {
  font-size: 12px;
  color: var(--text-secondary);
}
.p-meta {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  font-size: 12px;
}
.p-symbol {
  color: var(--text-secondary);
}
.p-min {
  color: var(--text-secondary);
}
.stake-card {
  margin-top: 8px;
}
.gain {
  color: #67c23a;
}
</style>
