<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import * as chainApi from '@/api/chain'
import type { WalletItem, WalletBalance } from '@/api/chain'
import { formatLong } from '@/utils/format'
import { coinDecimals } from '@/config/market'
import QRCode from 'qrcode'

const authStore = useAuthStore()
const userId = computed(() => authStore.userInfo?.id)

const wallets = ref<WalletItem[]>([])
const loading = ref(false)
const activeId = ref<string | number | undefined>(undefined)
const balances = ref<Record<string, WalletBalance[]>>({})
const balanceLoading = ref(false)

const CHAINS = ['ETH', 'BTC', 'TRON'] as const
const WALLET_TYPE_MAP: Record<string, string> = {
  HD: '助记词',
  PRIVATE: '私钥',
  READONLY: '只读',
}

async function loadWallets() {
  if (!userId.value) return
  loading.value = true
  try {
    wallets.value = await chainApi.walletList(userId.value)
  } finally {
    loading.value = false
  }
}

async function loadBalance(wallet: WalletItem) {
  if (!userId.value || !wallet.id) return
  balanceLoading.value = true
  try {
    balances.value[String(wallet.id)] = await chainApi.walletBalance(userId.value, wallet.id)
  } catch {
    balances.value[String(wallet.id)] = []
  } finally {
    balanceLoading.value = false
  }
}

function humanBalance(b: WalletBalance): string {
  if (b.symbol === 'ETH' && b.balance && b.decimals === 18) {
    // ETH native: wei → 展示
    return formatLong(Number(b.balance), b.decimals ?? 18, 6)
  }
  return formatLong(Number(b.balance ?? 0), b.decimals ?? 0, 6)
}

// ---------- 创建 ----------
const createRef = ref<FormInstance>()
const createLoading = ref(false)
const createForm = reactive({ chainCode: 'ETH' as string, name: '' })
const createdWallet = ref<WalletItem | null>(null)
const createdQr = ref('')

const createRules: FormRules = {
  chainCode: [{ required: true, message: '请选择链', trigger: 'change' }],
}

async function submitCreate() {
  if (!userId.value || !createRef.value) return
  const valid = await createRef.value.validate().catch(() => false)
  if (!valid) return
  createLoading.value = true
  try {
    const w = await chainApi.walletCreate({
      userId: userId.value,
      chainCode: createForm.chainCode,
      name: createForm.name || undefined,
    })
    createdWallet.value = w
    createdQr.value = w.address ? await QRCode.toDataURL(w.address, { width: 160, margin: 1 }) : ''
    ElMessage.success('钱包创建成功，请务必备份助记词')
    createForm.name = ''
    await loadWallets()
  } finally {
    createLoading.value = false
  }
}

function copyMnemonic() {
  if (!createdWallet.value?.mnemonic) return
  navigator.clipboard
    .writeText(createdWallet.value.mnemonic)
    .then(() => ElMessage.success('助记词已复制'))
    .catch(() => ElMessage.warning('复制失败'))
}

function copyAddress(addr: string) {
  if (!addr) return
  navigator.clipboard
    .writeText(addr)
    .then(() => ElMessage.success('地址已复制'))
    .catch(() => ElMessage.warning('复制失败，请手动复制'))
}

// ---------- 导入 ----------
const importRef = ref<FormInstance>()
const importLoading = ref(false)
const importForm = reactive({
  chainCode: 'ETH' as string,
  mnemonic: '',
  privateKey: '',
  name: '',
})
const importMode = ref<'mnemonic' | 'privateKey'>('mnemonic')

const importRules: FormRules = {
  chainCode: [{ required: true, message: '请选择链', trigger: 'change' }],
}

async function submitImport() {
  if (!userId.value || !importRef.value) return
  const valid = await importRef.value.validate().catch(() => false)
  if (!valid) return
  if (importMode.value === 'mnemonic' && !importForm.mnemonic.trim()) {
    ElMessage.warning('请输入助记词')
    return
  }
  if (importMode.value === 'privateKey' && !importForm.privateKey.trim()) {
    ElMessage.warning('请输入私钥')
    return
  }
  importLoading.value = true
  try {
    const w = await chainApi.walletImport({
      userId: userId.value,
      chainCode: importForm.chainCode,
      mnemonic: importMode.value === 'mnemonic' ? importForm.mnemonic.trim() : undefined,
      privateKey: importMode.value === 'privateKey' ? importForm.privateKey.trim() : undefined,
      name: importForm.name || undefined,
    })
    ElMessage.success('钱包导入成功')
    importForm.mnemonic = ''
    importForm.privateKey = ''
    importForm.name = ''
    await loadWallets()
  } finally {
    importLoading.value = false
  }
}

// ---------- 删除 ----------
async function removeWallet(w: WalletItem) {
  if (!w.id) return
  try {
    await ElMessageBox.confirm(`确定删除钱包 ${w.name || w.address} 吗？此操作不可撤销。`, '删除钱包', {
      type: 'warning',
    })
  } catch {
    return
  }
  // 后端暂未提供删除接口，提示
  ElMessage.info('删除功能待 M3 提供（当前为软删除预留）')
}

// ---------- 转账 (M3) ----------
const sendDialog = ref(false)
const sendLoading = ref(false)
const sendWallet = ref<WalletItem | null>(null)
const sendForm = reactive({ symbol: 'ETH' as string, toAddress: '', amount: '' })
const sendResult = ref<string>('')

function openSend(w: WalletItem) {
  sendWallet.value = w
  sendForm.symbol = 'ETH'
  sendForm.toAddress = ''
  sendForm.amount = ''
  sendResult.value = ''
  sendDialog.value = true
}

async function submitSend() {
  if (!userId.value || !sendWallet.value?.id) return
  if (!sendForm.toAddress.trim() || !sendForm.amount.trim()) {
    ElMessage.warning('请填写目标地址与金额')
    return
  }
  const dec = coinDecimals(sendForm.symbol) || 18
  const amount = toMinUnit(sendForm.amount, dec)
  if (amount <= 0) {
    ElMessage.warning('金额需大于 0')
    return
  }
  sendLoading.value = true
  sendResult.value = ''
  try {
    const r = await chainApi.walletSend(userId.value, sendWallet.value.id, {
      symbol: sendForm.symbol,
      toAddress: sendForm.toAddress.trim(),
      amount,
    })
    sendResult.value = r.txHash || ''
    ElMessage.success('转账已广播上链')
  } catch {
    // 错误已由拦截器提示
  } finally {
    sendLoading.value = false
  }
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

// ---------- 链上资产看板 (M3) ----------
const overviewLoading = ref(false)
const overview = ref<{ symbol: string; total: string; count: number; decimals: number }[]>([])

/** 汇总所有钱包的链上余额，按币种聚合 */
async function loadOverview() {
  if (!userId.value) return
  overviewLoading.value = true
  try {
    const ws = await chainApi.walletList(userId.value)
    const agg: Record<string, { total: bigint; count: number; decimals: number }> = {}
    for (const w of ws) {
      if (!w.id) continue
      try {
        const bs = await chainApi.walletBalance(userId.value, w.id)
        for (const b of bs) {
          const key = b.symbol || ''
          if (!agg[key]) agg[key] = { total: 0n, count: 0, decimals: b.decimals ?? 0 }
          agg[key].total += BigInt(b.balance ?? 0)
          agg[key].count += 1
          agg[key].decimals = b.decimals ?? 0
        }
      } catch {
        // 单钱包余额失败不阻断
      }
    }
    overview.value = Object.entries(agg).map(([symbol, v]) => ({
      symbol,
      total: v.total.toString(),
      count: v.count,
      decimals: v.decimals,
    }))
  } finally {
    overviewLoading.value = false
  }
}

function humanTotal(o: { total: string; decimals: number }): string {
  const n = BigInt(o.total)
  const dec = o.decimals
  const neg = n < 0n
  const abs = neg ? -n : n
  const s = abs.toString().padStart(dec + 1, '0')
  const intPart = s.slice(0, s.length - dec) || '0'
  const fracPart = s.slice(s.length - dec).replace(/0+$/, '')
  const human = fracPart ? `${intPart}.${fracPart}` : intPart
  return (neg ? '-' : '') + human
}

onMounted(() => {
  loadWallets()
  loadOverview()
})
</script>

<template>
  <div class="page-container wallet-page">
    <el-card shadow="never" class="g-card">
      <template #header>
        <div class="card-header">
          <span>Web3 钱包</span>
          <el-button size="small" :loading="loading" @click="loadWallets">刷新</el-button>
        </div>
      </template>

      <el-alert
        type="info"
        :closable="false"
        title="自托管钱包：私钥与助记词由你本人保管，交易所仅加密存储；请务必离线备份助记词，丢失无法找回。"
        style="margin-bottom: 16px"
      />

      <!-- 链上资产看板 (M3) -->
      <h4 class="section-title">
        链上资产看板
        <el-button size="small" :loading="overviewLoading" @click="loadOverview" style="margin-left: 12px">刷新</el-button>
      </h4>
      <el-row :gutter="16" v-loading="overviewLoading" style="margin-bottom: 8px">
        <el-col v-for="o in overview" :key="o.symbol" :span="8" :xs="24" :sm="12" :md="8">
          <el-card shadow="never" class="g-card overview-card tilt3d">
            <div class="ov-symbol">{{ o.symbol }}</div>
            <div class="ov-total num pulse-num">{{ humanTotal(o) }}</div>
            <div class="ov-sub">{{ o.count }} 个钱包累计</div>
          </el-card>
        </el-col>
      </el-row>
      <el-empty v-if="!overviewLoading && !overview.length" description="暂无链上资产（可先创建或导入钱包）" style="padding: 8px 0" />

      <!-- 钱包列表 -->
      <h4 class="section-title">我的钱包</h4>
      <el-table v-loading="loading" :data="wallets" stripe>
        <el-table-column label="名称" min-width="140">
          <template #default="{ row }">{{ row.name || row.walletType }}</template>
        </el-table-column>
        <el-table-column label="类型" width="100">
          <template #default="{ row }">
            <el-tag size="small">{{ WALLET_TYPE_MAP[row.walletType] || row.walletType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="链" width="90">
          <template #default="{ row }">{{ row.chainCode }}</template>
        </el-table-column>
        <el-table-column label="地址" min-width="300">
          <template #default="{ row }">
            <span class="addr-text">{{ row.address }}</span>
            <el-button link type="primary" size="small" @click="copyAddress(row.address)">复制</el-button>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              size="small"
              @click="() => { activeId = row.id; loadBalance(row) }"
            >
              查余额
            </el-button>
            <el-button link type="success" size="small" @click="openSend(row)">转账</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && !wallets.length" description="暂无钱包，请在下方创建或导入" />

      <!-- 选中钱包的余额 -->
      <template v-if="activeId">
        <h4 class="section-title">链上余额</h4>
        <el-table v-loading="balanceLoading" :data="balances[String(activeId)] || []" stripe size="small">
          <el-table-column prop="symbol" label="币种" width="120" />
          <el-table-column label="类型" width="120">
            <template #default="{ row }">{{ row.coinType === 'COIN' ? '原生' : '代币' }}</template>
          </el-table-column>
          <el-table-column label="余额">
            <template #default="{ row }">
              <span class="num">{{ humanBalance(row) }} {{ row.symbol }}</span>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!balanceLoading && !(balances[String(activeId)] || []).length" description="暂无链上余额数据" style="padding: 16px 0" />
      </template>

      <!-- 创建钱包 -->
      <h4 class="section-title">创建钱包</h4>
      <el-form ref="createRef" :model="createForm" :rules="createRules" label-width="80px" style="max-width: 420px">
        <el-form-item label="链" prop="chainCode">
          <el-select v-model="createForm.chainCode">
            <el-option v-for="c in CHAINS" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注名">
          <el-input v-model="createForm.name" placeholder="钱包备注名（可选）" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="createLoading" @click="submitCreate">创建钱包</el-button>
        </el-form-item>
      </el-form>

      <!-- 创建结果：显示助记词 + 二维码 -->
      <div v-if="createdWallet" class="created-box">
        <el-alert
          type="warning"
          :closable="false"
          title="请立即备份以下助记词！它将只显示这一次，后续不会再次明文返回。"
          style="margin-bottom: 12px"
        />
        <div class="created-inner">
          <div class="mnemonic-card">
            <div class="mnemonic-words">{{ createdWallet.mnemonic }}</div>
            <el-button type="primary" size="small" @click="copyMnemonic">复制助记词</el-button>
          </div>
          <div class="addr-block">
            <div class="qr-box">
              <img v-if="createdQr" :src="createdQr" alt="钱包地址二维码" width="160" height="160" />
            </div>
            <div class="addr-line">
              <span class="addr-text">{{ createdWallet.address }}</span>
              <el-button link type="primary" size="small" @click="copyAddress(createdWallet.address || '')">复制</el-button>
            </div>
          </div>
        </div>
      </div>

      <!-- 导入钱包 -->
      <h4 class="section-title">导入钱包</h4>
      <el-form ref="importRef" :model="importForm" :rules="importRules" label-width="80px" style="max-width: 520px">
        <el-form-item label="链" prop="chainCode">
          <el-select v-model="importForm.chainCode">
            <el-option v-for="c in CHAINS" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="方式">
          <el-radio-group v-model="importMode">
            <el-radio-button value="mnemonic">助记词</el-radio-button>
            <el-radio-button value="privateKey">私钥</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="importMode === 'mnemonic'" label="助记词">
          <el-input
            v-model="importForm.mnemonic"
            type="textarea"
            :rows="3"
            placeholder="输入 12/24 个英文助记词，空格分隔"
          />
        </el-form-item>
        <el-form-item v-else label="私钥">
          <el-input v-model="importForm.privateKey" placeholder="输入十六进制私钥（可带 0x）" />
        </el-form-item>
        <el-form-item label="备注名">
          <el-input v-model="importForm.name" placeholder="钱包备注名（可选）" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="importLoading" @click="submitImport">导入钱包</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 转账对话框 (M3) -->
    <el-dialog v-model="sendDialog" title="链上转账" width="480px">
      <el-alert
        type="warning"
        :closable="false"
        :title="`将从钱包 ${sendWallet?.name || sendWallet?.walletType} 广播一笔链上交易`"
        style="margin-bottom: 12px"
      />
      <el-form label-width="80px">
        <el-form-item label="转出地址">
          <span class="addr-text">{{ sendWallet?.address }}</span>
        </el-form-item>
        <el-form-item label="币种">
          <el-select v-model="sendForm.symbol">
            <el-option label="ETH (原生)" value="ETH" />
            <el-option label="USDT (ERC-20)" value="USDT" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标地址">
          <el-input v-model="sendForm.toAddress" placeholder="0x + 40 位十六进制地址" />
        </el-form-item>
        <el-form-item label="金额">
          <el-input v-model="sendForm.amount" placeholder="请输入金额" />
          <span class="unit-hint">单位 {{ sendForm.symbol }}（精度 {{ coinDecimals(sendForm.symbol) || 18 }}）</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="sendDialog = false">取消</el-button>
        <el-button type="primary" :loading="sendLoading" @click="submitSend">签名并广播</el-button>
      </template>
      <el-alert v-if="sendResult" type="success" :closable="false" title="已上链" style="margin-top: 8px">
        <template #default>
          <span class="addr-text">TX: {{ sendResult }}</span>
        </template>
      </el-alert>
    </el-dialog>
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
.addr-text {
  word-break: break-all;
  margin-right: 8px;
  font-family: var(--font-num);
}
.created-box {
  margin: 16px 0;
}
.created-inner {
  display: flex;
  gap: 24px;
  align-items: flex-start;
  flex-wrap: wrap;
}
.mnemonic-card {
  background: var(--bg-elevated);
  border: 1px solid var(--glass-border);
  border-radius: 10px;
  padding: 16px;
  max-width: 420px;
}
.mnemonic-words {
  font-size: 15px;
  line-height: 1.8;
  margin-bottom: 12px;
  user-select: all;
}
.qr-box {
  padding: 8px;
  background: #fff;
  border-radius: 8px;
}
.addr-block {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 10px;
}
.addr-line {
  display: flex;
  align-items: center;
  max-width: 340px;
}
.overview-card {
  text-align: center;
  padding: 8px 0;
}
.ov-symbol {
  font-size: 14px;
  color: var(--text-secondary);
  margin-bottom: 6px;
}
.ov-total {
  font-size: 24px;
  font-weight: 700;
  background: var(--accent-grad);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}
.ov-sub {
  font-size: 12px;
  color: var(--text-dim);
  margin-top: 6px;
}
.unit-hint {
  margin-left: 10px;
  font-size: 12px;
  color: var(--text-dim);
}
</style>
