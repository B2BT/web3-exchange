<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
const router = useRouter()
const route = useRoute()

// 顶栏导航（占位路由，后续 F2/F3 填充）
const menus = [
  { path: '/market', title: '行情' },
  { path: '/trade', title: '交易' },
  { path: '/asset', title: '资产' },
  { path: '/order', title: '订单' },
  { path: '/notify', title: '通知' },
  { path: '/user', title: '用户中心' },
]

const activeMenu = computed(() => {
  const hit = menus.find((m) => route.path.startsWith(m.path))
  return hit ? hit.path : ''
})

const displayName = computed(() => authStore.displayName)
const userAvatar = computed(() => authStore.userInfo?.avatar)

async function handleLogout() {
  try {
    await ElMessageBox.confirm('确定退出登录吗？', '提示', { type: 'warning' })
  } catch {
    return
  }
  await authStore.logout()
  ElMessage.success('已退出登录')
  router.push('/login')
}
</script>

<template>
  <el-container class="layout">
    <el-header class="layout-header">
      <div class="header-left">
        <div class="logo" @click="router.push('/market')">Web3 交易所</div>
        <el-menu
          mode="horizontal"
          :default-active="activeMenu"
          :ellipsis="false"
          class="header-menu"
          router
        >
          <el-menu-item v-for="m in menus" :key="m.path" :index="m.path">
            {{ m.title }}
          </el-menu-item>
        </el-menu>
      </div>
      <div class="header-right">
        <el-dropdown>
          <span class="user-info">
            <el-avatar :size="28" :src="userAvatar">
              {{ displayName.charAt(0).toUpperCase() }}
            </el-avatar>
            <span class="username">{{ displayName }}</span>
            <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item @click="router.push('/user')">
                <el-icon><User /></el-icon>用户中心
              </el-dropdown-item>
              <el-dropdown-item divided @click="handleLogout">
                <el-icon><SwitchButton /></el-icon>退出登录
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </el-header>

    <el-main class="layout-main">
      <router-view />
    </el-main>
  </el-container>
</template>

<style scoped>
.layout {
  height: 100vh;
}
.layout-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
  padding: 0 24px;
  height: 60px;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 32px;
}
.logo {
  font-size: 20px;
  font-weight: 700;
  color: #1a1c2c;
  cursor: pointer;
  white-space: nowrap;
}
.header-menu {
  border-bottom: none;
}
.header-right .user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  color: #303133;
}
.username {
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.layout-main {
  background: #f5f7fa;
  padding: 0;
}
</style>
