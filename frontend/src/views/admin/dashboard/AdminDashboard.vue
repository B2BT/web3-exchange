<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { adminAssetSummary, type AdminAssetSummaryItem } from '@/api/admin'
import { formatLong } from '@/utils/format'
import { coinDecimals } from '@/config/market'

const summary = ref<AdminAssetSummaryItem[]>([])
const loading = ref(false)
const error = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    summary.value = await adminAssetSummary()
  } catch {
    error.value = '资产汇总加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page-container">
    <el-card shadow="never" class="g-card">
      <template #header>
        <div class="card-header">
          <span>资产汇总</span>
          <el-button type="primary" text :loading="loading" @click="load">刷新</el-button>
        </div>
      </template>

      <el-alert
        v-if="error"
        :title="error"
        type="error"
        :closable="false"
        style="margin-bottom: 16px"
      />

      <!-- 汇总卡片 -->
      <div v-loading="loading" class="summary-grid">
        <div v-for="item in summary" :key="item.symbol" class="summary-card g-card">
          <div class="coin">{{ item.symbol || '-' }}</div>
          <div class="row">
            <span class="label">总余额</span>
            <span class="value num">{{ formatLong(item.totalAvailable, coinDecimals(item.symbol || '')) }}</span>
          </div>
          <div class="row">
            <span class="label">冻结</span>
            <span class="value frozen num">{{ formatLong(item.totalFrozen, coinDecimals(item.symbol || '')) }}</span>
          </div>
        </div>
        <el-empty
          v-if="!loading && summary.length === 0"
          description="暂无资产数据"
          :image-size="80"
          style="grid-column: 1 / -1"
        />
      </div>

      <h4 class="sub-title">明细</h4>
      <el-table v-loading="loading" :data="summary" stripe>
        <el-table-column prop="symbol" label="币种" width="160">
          <template #default="{ row }">
            <span class="coin-cell">{{ row.symbol }}</span>
          </template>
        </el-table-column>
        <el-table-column label="总余额（可用）" min-width="180">
          <template #default="{ row }">
            <span class="num">{{ formatLong(row.totalAvailable, coinDecimals(row.symbol || '')) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="冻结余额" min-width="180">
          <template #default="{ row }">
            <span class="num frozen">{{ formatLong(row.totalFrozen, coinDecimals(row.symbol || '')) }}</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.summary-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 16px;
  margin-bottom: 8px;
  min-height: 120px;
}
.summary-card {
  padding: 16px 18px;
}
.coin {
  font-size: 16px;
  font-weight: 700;
  margin-bottom: 12px;
  background: var(--accent-grad);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}
.row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
  padding: 4px 0;
}
.row .label {
  color: var(--text-dim);
}
.row .value {
  color: var(--text-primary);
}
.row .frozen,
.sub-title + * .frozen {
  color: var(--warning);
}
.sub-title {
  margin: 24px 0 10px;
  font-size: 14px;
}
.coin-cell {
  font-weight: 600;
}
</style>
