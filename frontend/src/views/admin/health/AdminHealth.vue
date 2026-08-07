<script setup lang="ts">
import { onMounted, ref } from 'vue'
import * as adminApi from '@/api/adminB'
import type { ServiceHealth } from '@/api/adminB'

const list = ref<ServiceHealth[]>([])
const loading = ref(false)

function fmtMem(b: number | undefined): string {
  if (!b) return '0 MB'
  return (b / 1024 / 1024).toFixed(0) + ' MB'
}

async function load() {
  loading.value = true
  try {
    const d = await adminApi.adminHealth(1, 100)
    list.value = d?.records ?? []
  } finally {
    loading.value = false
  }
}

function statusTag(s: number | undefined) {
  return s === 1 ? { type: 'success' as const, label: 'UP' } : { type: 'danger' as const, label: 'DOWN' }
}

onMounted(load)
</script>

<template>
  <div class="admin-page g-card">
    <div class="toolbar">
      <span style="font-weight: 600">服务监控</span>
      <div style="flex: 1"></div>
      <el-button size="small" :loading="loading" @click="load">刷新</el-button>
    </div>

    <el-row :gutter="16">
      <el-col :span="6" v-for="h in list" :key="h.id">
        <el-card shadow="never" class="g-card svc-card tilt3d">
          <div class="svc-name">{{ h.serviceName }}</div>
          <el-tag :type="statusTag(h.status).type" size="small">{{ statusTag(h.status).label }}</el-tag>
          <div class="svc-meta">
            <div><span class="dim">实例</span> {{ h.instanceIp }}:{{ h.port }}</div>
            <div><span class="dim">内存</span> {{ fmtMem(h.memoryUsed) }} / {{ fmtMem(h.memoryTotal) }}</div>
            <div><span class="dim">心跳</span> {{ h.lastHeartbeat || '—' }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>
    <el-empty v-if="!loading && !list.length" description="暂无服务上报，等待各服务心跳" />
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
.svc-card {
  text-align: center;
  padding: 12px 0;
  margin-bottom: 16px;
}
.svc-name {
  font-weight: 600;
  margin-bottom: 8px;
}
.svc-meta {
  margin-top: 12px;
  font-size: 12px;
  text-align: left;
  line-height: 2;
}
.dim {
  color: var(--text-dim);
}
</style>
