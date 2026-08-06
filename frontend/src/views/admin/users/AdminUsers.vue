<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  adminUserList,
  adminUserBan,
  adminUserUnban,
  type AdminUserItem,
} from '@/api/admin'
import type { PageData } from '@/api/order'

const STATUS: Record<number, { text: string; type: string }> = {
  1: { text: '正常', type: 'success' },
  2: { text: '封禁', type: 'danger' },
}
const ROLE: Record<string, string> = { USER: '普通用户', ADMIN: '管理员' }

const list = ref<AdminUserItem[]>([])
const loading = ref(false)
const total = ref(0)
const page = ref(1)
const size = ref(20)
const keyword = ref('')
const acting = ref('')

async function load(p = page.value) {
  loading.value = true
  page.value = p
  try {
    const res: PageData<AdminUserItem> = await adminUserList({
      page: page.value,
      size: size.value,
      keyword: keyword.value.trim() || undefined,
    })
    list.value = res.records || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

async function handleSearch() {
  load(1)
}

async function toggleStatus(row: AdminUserItem) {
  if (!row.id) return
  const isBan = row.status === 1
  try {
    await ElMessageBox.confirm(
      `确定${isBan ? '封禁' : '解封'}用户「${row.username}」吗？`,
      '提示',
      { type: 'warning' },
    )
  } catch {
    return
  }
  acting.value = row.id
  try {
    if (isBan) {
      await adminUserBan(row.id)
      ElMessage.success('已封禁该用户')
    } else {
      await adminUserUnban(row.id)
      ElMessage.success('已解封该用户')
    }
    load(page.value)
  } catch {
    // 错误已由拦截器提示
  } finally {
    acting.value = ''
  }
}

onMounted(() => load(1))
</script>

<template>
  <div class="page-container">
    <el-card shadow="never" class="g-card">
      <template #header>
        <div class="card-header">
          <span>用户管理</span>
          <div class="search-row">
            <el-input
              v-model="keyword"
              placeholder="按用户名/手机号搜索"
              clearable
              style="width: 260px"
              @keyup.enter="handleSearch"
              @clear="handleSearch"
            >
              <template #prefix><el-icon><Search /></el-icon></template>
            </el-input>
            <el-button type="primary" @click="handleSearch">搜索</el-button>
          </div>
        </div>
      </template>

      <el-table v-loading="loading" :data="list" stripe>
        <el-table-column prop="id" label="用户ID" min-width="180">
          <template #default="{ row }"><span class="num">{{ row.id }}</span></template>
        </el-table-column>
        <el-table-column prop="username" label="用户名" min-width="120" />
        <el-table-column prop="email" label="邮箱" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.email || '-' }}</template>
        </el-table-column>
        <el-table-column prop="phone" label="手机号" min-width="130">
          <template #default="{ row }">{{ row.phone || '-' }}</template>
        </el-table-column>
        <el-table-column label="角色" width="100">
          <template #default="{ row }">
            <el-tag :type="row.role === 'ADMIN' ? 'primary' : 'info'" size="small">
              {{ ROLE[row.role || 'USER'] || row.role }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="(STATUS[row.status || 0] || {}).type" size="small">
              {{ (STATUS[row.status || 0] || {}).text || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="registerTime" label="注册时间" width="180">
          <template #default="{ row }">{{ row.registerTime || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="110">
          <template #default="{ row }">
            <el-button
              v-if="row.role !== 'ADMIN'"
              :type="row.status === 1 ? 'danger' : 'success'"
              link
              :loading="acting === row.id"
              @click="toggleStatus(row)"
            >
              {{ row.status === 1 ? '解封' : '封禁' }}
            </el-button>
            <span v-else>-</span>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && list.length === 0" description="暂无用户" :image-size="80" />

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
