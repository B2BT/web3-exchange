<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { getOrder, getTrades, type OrderItem, type TradeItem } from '@/api/order'
import { depositList, withdrawList, type DepositItem, type WithdrawItem } from '@/api/chain'
import { formatLong } from '@/utils/format'
import { coinDecimals } from '@/config/market'

const authStore = useAuthStore()
const userId = computed(() => authStore.userInfo?.id)

const ORDER_STATUS: Record<number, { text: string; type: string }> = {
  0: { text: '待成交', type: 'warning' },
  1: { text: '部分成交', type: 'primary' },
  2: { text: '已成交', type: 'success' },
  3: { text: '已撤销', type: 'info' },
  4: { text: '已拒绝', type: 'danger' },
}
const SIDE: Record<number, string> = { 1: '买入', 2: '卖出' }

// ---------- 按单号查询订单 + 成交 ----------
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
  loadDeposits()
  loadWithdraws()
})
</script>

<template>
  <div class="page-container">
    <el-card shadow="never">
      <template #header>
        <span>订单与资金流水</span>
      </template>

      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="当前后端订单域暂无「按用户分页列表」接口（仅支持按订单号查询）。以下以充值/提现资金记录作为活动流水展示；订单明细请使用「订单查询」按订单号检索。"
        style="margin-bottom: 16px"
      />

      <el-tabs>
        <!-- 订单查询 -->
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
                <el-tag :type="order.side === 1 ? 'danger' : 'success'" size="small">
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
                {{ formatLong(order.avgPrice, coinDecimals(order.quoteCoin || 'USDT')) }}
              </el-descriptions-item>
              <el-descriptions-item label="数量">
                {{ formatLong(order.quantity, coinDecimals(order.baseCoin || '')) }}
              </el-descriptions-item>
              <el-descriptions-item label="已成交">
                {{ formatLong(order.filledAmount, coinDecimals(order.baseCoin || '')) }}
              </el-descriptions-item>
              <el-descriptions-item label="创建时间">{{ order.createTime || '-' }}</el-descriptions-item>
            </el-descriptions>

            <h4 class="sub-title">成交明细</h4>
            <el-table :data="trades" stripe>
              <el-table-column prop="tradeNo" label="成交号" />
              <el-table-column label="价格">
                <template #default="{ row }">
                  {{ formatLong(row.price, coinDecimals(row.symbol?.split('/')[1] || 'USDT')) }}
                </template>
              </el-table-column>
              <el-table-column label="数量">
                <template #default="{ row }">
                  {{ formatLong(row.quantity, coinDecimals(row.symbol?.split('/')[0] || '')) }}
                </template>
              </el-table-column>
              <el-table-column label="成交额">
                <template #default="{ row }">
                  {{ formatLong(row.quoteAmount, coinDecimals(row.symbol?.split('/')[1] || 'USDT')) }}
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
                {{ formatLong(row.amount, coinDecimals(row.symbol)) }} {{ row.symbol }}
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
                {{ formatLong(row.amount, coinDecimals(row.symbol)) }} {{ row.symbol }}
              </template>
            </el-table-column>
            <el-table-column label="手续费">
              <template #default="{ row }">
                {{ formatLong(row.fee, coinDecimals(row.symbol)) }}
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
