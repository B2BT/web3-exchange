<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import * as notifyApi from '@/api/notify'
import type { NotificationItem } from '@/api/notify'
import { formatLong } from '@/utils/format'
import { coinDecimals } from '@/config/market'

const authStore = useAuthStore()
const userId = computed(() => authStore.userInfo?.id)

const list = ref<NotificationItem[]>([])
const loading = ref(false)
const total = ref(0)
const page = ref(1)
const unread = ref(0)

const TYPE_MAP: Record<string, string> = {
  DEPOSIT_CONFIRMED: '充值到账',
  WITHDRAW_SUCCESS: '提现成功',
  TRADE_FILLED: '成交通知',
}

async function loadList(p = 1) {
  if (!userId.value) return
  loading.value = true
  page.value = p
  try {
    const res = await notifyApi.notifyList(userId.value, p, 20)
    list.value = res.records || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

async function loadUnread() {
  if (!userId.value) return
  try {
    unread.value = await notifyApi.unreadCount(userId.value)
  } catch {
    unread.value = 0
  }
}

async function markOne(item: NotificationItem) {
  if (!userId.value || !item.id || item.isRead === 1) return
  try {
    await notifyApi.markRead(item.id, userId.value)
    item.isRead = 1
    unread.value = Math.max(0, unread.value - 1)
  } catch {
    /* 已读失败保持原状 */
  }
}

async function markAll() {
  if (!userId.value) return
  try {
    const n = await notifyApi.markAllRead(userId.value)
    if (n > 0) ElMessage.success(`已将 ${n} 条通知标记为已读`)
    list.value.forEach((it) => (it.isRead = 1))
    unread.value = 0
  } catch {
    /* 忽略 */
  }
}

onMounted(() => {
  loadUnread()
  loadList()
})
</script>

<template>
  <div class="page-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <div class="header-title">
            <span>站内通知</span>
            <el-badge v-if="unread > 0" :value="unread" class="unread-badge" type="danger" />
            <span v-else class="unread-zero">暂无未读</span>
          </div>
          <el-button size="small" :disabled="unread === 0" @click="markAll">全部标为已读</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="list" stripe>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-badge v-if="row.isRead === 0" is-dot type="danger" />
            <span v-else class="read-mark">已读</span>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="130">
          <template #default="{ row }">
            <el-tag size="small">{{ TYPE_MAP[row.type] || row.type || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="标题" min-width="160" />
        <el-table-column prop="content" label="内容" min-width="220" show-overflow-tooltip />
        <el-table-column label="关联金额" width="150">
          <template #default="{ row }">
            <span v-if="row.amount != null">
              {{ formatLong(row.amount, coinDecimals(row.symbol || '')) }} {{ row.symbol }}
            </span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="时间" width="170" />
        <el-table-column label="操作" width="100" align="center">
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              :disabled="row.isRead === 1"
              @click="markOne(row)"
            >
              标记已读
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && !list.length" description="暂无通知" />

      <el-pagination
        v-if="total > 20"
        layout="prev, pager, next"
        :total="total"
        :page-size="20"
        :current-page="page"
        @current-change="loadList"
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
.header-title {
  display: flex;
  align-items: center;
  gap: 10px;
}
.unread-badge {
  margin-left: 8px;
}
.unread-zero {
  font-size: 12px;
  color: #909399;
}
.read-mark {
  font-size: 12px;
  color: #c0c4cc;
}
</style>
