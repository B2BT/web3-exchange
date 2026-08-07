<script setup lang="ts">
import { onMounted, ref } from 'vue'
import * as adminApi from '@/api/adminB'
import type { AdminAudit } from '@/api/adminB'

const list = ref<AdminAudit[]>([])
const total = ref(0)
const loading = ref(false)
const page = ref(1)
const size = ref(20)

async function load() {
  loading.value = true
  try {
    const d = await adminApi.adminAudits(page.value, size.value)
    list.value = d?.records ?? []
    total.value = d?.total ?? 0
  } finally {
    loading.value = false
  }
}

function actionTag(a: string | undefined) {
  if (!a) return { type: 'info' as const, label: '—' }
  if (a.startsWith('CREATE') || a === 'PUBLISH' || a === 'LISTING') return { type: 'success' as const, label: a }
  if (a === 'DELETE' || a === 'SUSPEND' || a === 'OFFLINE') return { type: 'danger' as const, label: a }
  return { type: 'warning' as const, label: a }
}

onMounted(load)
</script>

<template>
  <div class="admin-page g-card">
    <div class="toolbar">
      <span style="font-weight: 600">管理员审计日志</span>
      <div style="flex: 1"></div>
      <el-button size="small" :loading="loading" @click="load">刷新</el-button>
    </div>

    <el-table v-loading="loading" :data="list" stripe>
      <el-table-column prop="createTime" label="时间" min-width="170" />
      <el-table-column label="操作" width="120">
        <template #default="{ row }">
          <el-tag :type="actionTag(row.action).type" size="small">{{ actionTag(row.action).label }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="targetType" label="目标" width="130" />
      <el-table-column prop="targetId" label="目标ID" min-width="160" />
      <el-table-column prop="detail" label="详情" min-width="220" show-overflow-tooltip />
      <el-table-column prop="adminUsername" label="操作人" width="110" />
      <el-table-column prop="ip" label="IP" width="130" />
    </el-table>

    <el-pagination
      v-model:current-page="page"
      v-model:page-size="size"
      :total="total"
      layout="total, prev, pager, next"
      style="margin-top: 16px; justify-content: flex-end"
      @change="load"
    />
  </div>
</template>

<style scoped>
.admin-page {
  padding: 20px;
}
.toolbar {
  display: flex;
  align-items: center;
  margin-bottom: 16px;
}
</style>
