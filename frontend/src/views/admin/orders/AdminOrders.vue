<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { adminOrderList } from '@/api/admin'
import type { OrderItem } from '@/api/order'
import { formatLong } from '@/utils/format'
import { coinDecimals, symbolParts, PRICE_DECIMALS } from '@/config/market'

const ORDER_STATUS: Record<number, { text: string; type: string }> = {
  0: { text: '待成交', type: 'warning' },
  1: { text: '部分成交', type: 'primary' },
  2: { text: '已完成', type: 'success' },
  3: { text: '已取消', type: 'info' },
  4: { text: '已拒绝', type: 'danger' },
}
const SIDE: Record<number, string> = { 1: '买入', 2: '卖出' }
const ORDER_TYPE: Record<number, string> = { 1: '限价', 2: '市价' }

function sideType(side: number | undefined): string {
  return side === 1 ? 'success' : 'danger'
}

const list = ref<OrderItem[]>([])
const loading = ref(false)
const total = ref(0)
const page = ref(1)
const size = ref(20)
const filters = reactive({ symbol: '', status: '' as number | '' })

const STATUS_OPTIONS = Object.entries(ORDER_STATUS).map(([k, v]) => ({
  value: Number(k),
  label: v.text,
}))

async function load(p = page.value) {
  loading.value = true
  page.value = p
  try {
    const res = await adminOrderList({
      page: page.value,
      size: size.value,
      symbol: filters.symbol.trim() || undefined,
      status: filters.status === '' ? undefined : filters.status,
    })
    list.value = res.records || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  load(1)
}

onMounted(() => load(1))
</script>

<template>
  <div class="page-container">
    <el-card shadow="never" class="g-card">
      <template #header>
        <div class="card-header">
          <span>全站订单</span>
          <div class="search-row">
            <el-input
              v-model="filters.symbol"
              placeholder="交易对，如 BTC/USDT"
              clearable
              style="width: 200px"
              @keyup.enter="handleSearch"
              @clear="handleSearch"
            />
            <el-select
              v-model="filters.status"
              placeholder="订单状态"
              clearable
              style="width: 150px"
              @change="handleSearch"
            >
              <el-option v-for="opt in STATUS_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
            </el-select>
            <el-button type="primary" @click="handleSearch">查询</el-button>
          </div>
        </div>
      </template>

      <el-table v-loading="loading" :data="list" stripe>
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
            <span class="num">{{ formatLong(row.filledAmount, coinDecimals(symbolParts(row.symbol || '').base)) }}</span>
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
      <el-empty v-if="!loading && list.length === 0" description="暂无订单" :image-size="80" />

      <el-pagination
        v-if="total > size"
        layout="total, prev, pager, next, sizes"
        :total="total"
        :page-size="size"
        :current-page="page"
        :page-sizes="[10, 20, 50, 100]"
        @current-change="load"
        @size-change="(s: number) => { size = s; load(1) }"
        style="margin-top: 12px; justify-content: flex-end"
      />
    </el-card>
  </div>
</template>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.search-row {
  display: flex;
  gap: 10px;
}
</style>
