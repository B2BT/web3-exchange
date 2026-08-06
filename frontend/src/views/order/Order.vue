<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import {
  getOrder,
  getTrades,
  listOrders,
  cancelOrder,
  type OrderItem,
  type PageData,
  type TradeItem,
} from '@/api/order'
import { depositList, withdrawList, type DepositItem, type WithdrawItem } from '@/api/chain'
import { formatLong } from '@/utils/format'
import { coinDecimals, symbolParts, PRICE_DECIMALS } from '@/config/market'

const authStore = useAuthStore()
const userId = computed(() => authStore.userInfo?.id)

const ORDER_STATUS: Record<number, { text: string; type: string }> = {
  0: { text: '待成交', type: 'warning' },
  1: { text: '部分成交', type: 'primary' },
  2: { text: '已完成', type: 'success' },
  3: { text: '已取消', type: 'info' },
  4: { text: '已拒绝', type: 'danger' },
}
const SIDE: Record<number, string> = { 1: '买入', 2: '卖出' }
const ORDER_TYPE: Record<number, string> = { 1: '限价', 2: '市价' }

/** 方向着色：买入绿、卖出红 */
function sideType(side: number | undefined): string {
  return side === 1 ? 'success' : 'danger'
}

/** 已成交 = 数量 - 剩余（均为 Long 最小单位） */
function filledOf(row: OrderItem): number {
  if (row.filledAmount != null && row.filledAmount !== 0) return row.filledAmount
  const q = row.quantity ?? 0
  const r = row.remaining ?? 0
  return Math.max(q - r, 0)
}

// ---------- 当前委托（status 0,1，可撤单） ----------
const activeOrders = ref<OrderItem[]>([])
const activeLoading = ref(false)
const activeTotal = ref(0)
const activePage = ref(1)
const activeSize = ref(20)

// ---------- 历史订单（status 2,3,4） ----------
const histOrders = ref<OrderItem[]>([])
const histLoading = ref(false)
const histTotal = ref(0)
const histPage = ref(1)
const histSize = ref(20)

async function loadActiveOrders(page = 1) {
  if (!userId.value) return
  activeLoading.value = true
  activePage.value = page
  try {
    // 当前委托聚合 status 0,1
    const [r0, r1] = await Promise.all([
      listOrders({ userId: userId.value, status: 0, page, size: activeSize.value }),
      listOrders({ userId: userId.value, status: 1, page, size: activeSize.value }),
    ])
    // 合并两状态并按 createTime 降序（后端已按时间排，直接拼接）
    activeOrders.value = [...(r0.records || []), ...(r1.records || [])]
    activeTotal.value = (r0.total || 0) + (r1.total || 0)
  } finally {
    activeLoading.value = false
  }
}

async function loadHistory(page = 1) {
  if (!userId.value) return
  histLoading.value = true
  histPage.value = page
  try {
    // 历史订单聚合 status 2,3,4
    const [r2, r3, r4] = await Promise.all([
      listOrders({ userId: userId.value, status: 2, page, size: histSize.value }),
      listOrders({ userId: userId.value, status: 3, page, size: histSize.value }),
      listOrders({ userId: userId.value, status: 4, page, size: histSize.value }),
    ])
    histOrders.value = [...(r2.records || []), ...(r3.records || []), ...(r4.records || [])]
    histTotal.value = (r2.total || 0) + (r3.total || 0) + (r4.total || 0)
  } finally {
    histLoading.value = false
  }
}

// ---------- 撤单 ----------
const cancelling = ref<string>('')
async function handleCancel(row: OrderItem) {
  if (!userId.value || !row.orderNo) return
  cancelling.value = row.orderNo
  try {
    await cancelOrder(userId.value, row.orderNo)
    ElMessage.success('撤单成功')
    // 撤单后刷新当前委托
    loadActiveOrders(activePage.value)
  } catch {
    // 错误已由拦截器提示
  } finally {
    cancelling.value = ''
  }
}

// ---------- 按单号查询（可选入口） ----------
const query = reactive({ orderNo: '' })
const orderLoading = ref(false)
const order = ref<OrderItem | null>(null)
const trades = ref<TradeItem[]>([])
const orderSearched = ref(false)

async function searchOrder() {
  if (!userId.value || !query.orderNo.trim()) {
    ElMessage.warning('请输入订单号')
    return
  }
  orderLoading.value = true
  order.value = null
  trades.value = []
  orderSearched.value = false
  try {
    order.value = await getOrder(userId.value, query.orderNo.trim())
    try {
      trades.value = await getTrades(userId.value, query.orderNo.trim())
    } catch {
      trades.value = []
    }
    orderSearched.value = true
  } finally {
    orderLoading.value = false
  }
}

// ---------- 充值记录 ----------
const deposits = ref<DepositItem[]>([])
const depLoading = ref(false)
const depTotal = ref(0)
const depPage = ref(1)

async function loadDeposits(page = 1) {
  if (!userId.value) return
  depLoading.value = true
  depPage.value = page
  try {
    const res = await depositList(userId.value, page, 20)
    deposits.value = res.records || []
    depTotal.value = res.total || 0
  } finally {
    depLoading.value = false
  }
}

const DEPOSIT_STATUS: Record<number, { text: string; type: string }> = {
  0: { text: '监听中', type: 'info' },
  1: { text: '待确认', type: 'warning' },
  2: { text: '已入账', type: 'success' },
  3: { text: '失败', type: 'danger' },
}

// ---------- 提现记录 ----------
const withdraws = ref<WithdrawItem[]>([])
const wdLoading = ref(false)
const wdTotal = ref(0)
const wdPage = ref(1)

async function loadWithdraws(page = 1) {
  if (!userId.value) return
  wdLoading.value = true
  wdPage.value = page
  try {
    const res = await withdrawList(userId.value, page, 20)
    withdraws.value = res.records || []
    wdTotal.value = res.total || 0
  } finally {
    wdLoading.value = false
  }
}

const WITHDRAW_STATUS: Record<number, { text: string; type: string }> = {
  0: { text: '待审核', type: 'info' },
  1: { text: '审核中', type: 'warning' },
  2: { text: '处理中', type: 'warning' },
  3: { text: '成功', type: 'success' },
  4: { text: '拒绝', type: 'danger' },
  5: { text: '失败回滚', type: 'danger' },
}

onMounted(() => {
  loadActiveOrders()
  loadHistory()
  loadDeposits()
  loadWithdraws()
})
</script>

<template>
  <div class="page-container">
    <el-card shadow="never" class="g-card">
      <template #header>
        <span>我的委托 / 历史订单</span>
      </template>

      <el-tabs>
        <!-- 当前委托 -->
        <el-tab-pane label="当前委托" lazy>
          <el-table v-loading="activeLoading" :data="activeOrders" stripe>
            <el-table-column label="时间" width="180">
              <template #default="{ row }">{{ row.createTime || '-' }}</template>
            </el-table-column>
            <el-table-column prop="symbol" label="交易对" min-width="110" />
            <el-table-column label="方向" width="90">
              <template #default="{ row }">
                <el-tag :type="sideType(row.side)" size="small" effect="dark">
                  {{ SIDE[row.side || 0] || '-' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="类型" width="80">
              <template #default="{ row }">{{ ORDER_TYPE[row.orderType || 0] || '-' }}</template>
            </el-table-column>
            <el-table-column label="价格" min-width="120">
              <template #default="{ row }">
                <span class="num">{{ formatLong(row.price, PRICE_DECIMALS) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="数量" min-width="120">
              <template #default="{ row }">
                <span class="num">{{ formatLong(row.quantity, coinDecimals(symbolParts(row.symbol || '').base)) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="已成交" min-width="120">
              <template #default="{ row }">
                <span class="num">{{ formatLong(filledOf(row), coinDecimals(symbolParts(row.symbol || '').base)) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="(ORDER_STATUS[row.status || 0] || {}).type" size="small">
                  {{ (ORDER_STATUS[row.status || 0] || {}).text || row.status }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="90">
              <template #default="{ row }">
                <el-button
                  v-if="row.status === 0 || row.status === 1"
                  type="danger"
                  link
                  :loading="cancelling === row.orderNo"
                  @click="handleCancel(row)"
                >
                  撤单
                </el-button>
                <span v-else>-</span>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!activeLoading && activeOrders.length === 0" description="暂无当前委托" :image-size="80" />
          <el-pagination
            v-if="activeTotal > activeSize"
            layout="total, prev, pager, next, sizes"
            :total="activeTotal"
            :page-size="activeSize"
            :current-page="activePage"
            :page-sizes="[10, 20, 50, 100]"
            @current-change="loadActiveOrders"
            @size-change="(s: number) => { activeSize = s; loadActiveOrders(1) }"
            style="margin-top: 12px; justify-content: flex-end"
          />
        </el-tab-pane>

        <!-- 历史订单 -->
        <el-tab-pane label="历史订单" lazy>
          <el-table v-loading="histLoading" :data="histOrders" stripe>
            <el-table-column label="时间" width="180">
              <template #default="{ row }">{{ row.createTime || '-' }}</template>
            </el-table-column>
            <el-table-column prop="symbol" label="交易对" min-width="110" />
            <el-table-column label="方向" width="90">
              <template #default="{ row }">
                <el-tag :type="sideType(row.side)" size="small" effect="dark">
                  {{ SIDE[row.side || 0] || '-' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="类型" width="80">
              <template #default="{ row }">{{ ORDER_TYPE[row.orderType || 0] || '-' }}</template>
            </el-table-column>
            <el-table-column label="价格" min-width="120">
              <template #default="{ row }">
                <span class="num">{{ formatLong(row.price, PRICE_DECIMALS) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="数量" min-width="120">
              <template #default="{ row }">
                <span class="num">{{ formatLong(row.quantity, coinDecimals(symbolParts(row.symbol || '').base)) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="已成交" min-width="120">
              <template #default="{ row }">
                <span class="num">{{ formatLong(filledOf(row), coinDecimals(symbolParts(row.symbol || '').base)) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="(ORDER_STATUS[row.status || 0] || {}).type" size="small">
                  {{ (ORDER_STATUS[row.status || 0] || {}).text || row.status }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!histLoading && histOrders.length === 0" description="暂无历史订单" :image-size="80" />
          <el-pagination
            v-if="histTotal > histSize"
            layout="total, prev, pager, next, sizes"
            :total="histTotal"
            :page-size="histSize"
            :current-page="histPage"
            :page-sizes="[10, 20, 50, 100]"
            @current-change="loadHistory"
            @size-change="(s: number) => { histSize = s; loadHistory() }"
            style="margin-top: 12px; justify-content: flex-end"
          />
        </el-tab-pane>

        <!-- 按单号查询 -->
        <el-tab-pane label="订单查询" lazy>
          <div class="search-row">
            <el-input
              v-model="query.orderNo"
              placeholder="输入订单号（orderNo）"
              clearable
              style="width: 320px"
              @keyup.enter="searchOrder"
            />
            <el-button type="primary" :loading="orderLoading" @click="searchOrder">查询</el-button>
          </div>

          <el-empty
            v-if="orderSearched && !order"
            description="未查询到该订单"
            style="margin-top: 20px"
          />

          <template v-if="order">
            <el-descriptions :column="3" border style="margin-top: 16px">
              <el-descriptions-item label="订单号">{{ order.orderNo }}</el-descriptions-item>
              <el-descriptions-item label="交易对">{{ order.symbol }}</el-descriptions-item>
              <el-descriptions-item label="方向">
                <el-tag :type="sideType(order.side)" size="small">
                  {{ SIDE[order.side || 0] || order.side }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="类型">
                {{ order.orderType === 1 ? '限价' : order.orderType === 2 ? '市价' : '-' }}
              </el-descriptions-item>
              <el-descriptions-item label="状态">
                <el-tag :type="(ORDER_STATUS[order.status || 0] || {}).type" size="small">
                  {{ (ORDER_STATUS[order.status || 0] || {}).text || order.status }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="均价">
                <span class="num">{{ formatLong(order.avgPrice, coinDecimals(order.quoteCoin || 'USDT')) }}</span>
              </el-descriptions-item>
              <el-descriptions-item label="数量">
                <span class="num">{{ formatLong(order.quantity, coinDecimals(order.baseCoin || '')) }}</span>
              </el-descriptions-item>
              <el-descriptions-item label="已成交">
                <span class="num">{{ formatLong(filledOf(order), coinDecimals(order.baseCoin || '')) }}</span>
              </el-descriptions-item>
              <el-descriptions-item label="创建时间">{{ order.createTime || '-' }}</el-descriptions-item>
            </el-descriptions>

            <h4 class="sub-title">成交明细</h4>
            <el-table :data="trades" stripe>
              <el-table-column prop="tradeNo" label="成交号" />
              <el-table-column label="价格">
                <template #default="{ row }">
                  <span class="num">{{ formatLong(row.price, coinDecimals(row.symbol?.split('/')[1] || 'USDT')) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="数量">
                <template #default="{ row }">
                  <span class="num">{{ formatLong(row.quantity, coinDecimals(row.symbol?.split('/')[0] || '')) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="成交额">
                <template #default="{ row }">
                  <span class="num">{{ formatLong(row.quoteAmount, coinDecimals(row.symbol?.split('/')[1] || 'USDT')) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="时间">
                <template #default="{ row }">{{ row.tradeTime || '-' }}</template>
              </el-table-column>
            </el-table>
            <el-empty v-if="trades.length === 0" description="暂无成交明细" :image-size="80" />
          </template>
        </el-tab-pane>

        <!-- 充值记录 -->
        <el-tab-pane label="充值记录" lazy>
          <el-table v-loading="depLoading" :data="deposits" stripe>
            <el-table-column prop="symbol" label="币种" width="90" />
            <el-table-column prop="chainCode" label="链" width="90" />
            <el-table-column label="金额">
              <template #default="{ row }">
                <span class="num">{{ formatLong(row.amount, coinDecimals(row.symbol)) }} {{ row.symbol }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="txHash" label="交易哈希" min-width="180" show-overflow-tooltip />
            <el-table-column label="确认" width="110">
              <template #default="{ row }">
                {{ row.confirmations ?? '-' }}/{{ row.requiredConfirmations ?? '-' }}
              </template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="(DEPOSIT_STATUS[row.status || 0] || {}).type" size="small">
                  {{ (DEPOSIT_STATUS[row.status || 0] || {}).text || row.status }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createTime" label="时间" width="170" />
          </el-table>
          <el-pagination
            v-if="depTotal > 20"
            layout="prev, pager, next"
            :total="depTotal"
            :page-size="20"
            :current-page="depPage"
            @current-change="loadDeposits"
            style="margin-top: 12px; justify-content: flex-end"
          />
        </el-tab-pane>

        <!-- 提现记录 -->
        <el-tab-pane label="提现记录" lazy>
          <el-table v-loading="wdLoading" :data="withdraws" stripe>
            <el-table-column prop="symbol" label="币种" width="90" />
            <el-table-column prop="chainCode" label="链" width="90" />
            <el-table-column prop="toAddress" label="目标地址" min-width="180" show-overflow-tooltip />
            <el-table-column label="金额">
              <template #default="{ row }">
                <span class="num">{{ formatLong(row.amount, coinDecimals(row.symbol)) }} {{ row.symbol }}</span>
              </template>
            </el-table-column>
            <el-table-column label="手续费">
              <template #default="{ row }">
                <span class="num">{{ formatLong(row.fee, coinDecimals(row.symbol)) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="110">
              <template #default="{ row }">
                <el-tag :type="(WITHDRAW_STATUS[row.status || 0] || {}).type" size="small">
                  {{ (WITHDRAW_STATUS[row.status || 0] || {}).text || row.status }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createTime" label="时间" width="170" />
          </el-table>
          <el-pagination
            v-if="wdTotal > 20"
            layout="prev, pager, next"
            :total="wdTotal"
            :page-size="20"
            :current-page="wdPage"
            @current-change="loadWithdraws"
            style="margin-top: 12px; justify-content: flex-end"
          />
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<style scoped>
.search-row {
  display: flex;
  gap: 12px;
}
.sub-title {
  margin: 20px 0 10px;
}
</style>
