<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
const router = useRouter()
const route = useRoute()
const drawerVisible = ref(false)

const menus = [
  { path: '/admin/dashboard', title: '仪表盘', icon: 'Odometer' },
  { path: '/admin/users', title: '用户管理', icon: 'User' },
  { path: '/admin/orders', title: '订单管理', icon: 'List' },
  { path: '/admin/withdrawals', title: '提现审核', icon: 'Wallet' },
  { path: '/admin/announcements', title: '公告管理', icon: 'Bell' },
  { path: '/admin/health', title: '服务监控', icon: 'Monitor' },
  { path: '/admin/audit', title: '审计日志', icon: 'Document' },
  { path: '/admin/symbols', title: '交易对管理', icon: 'Coin' },
  { path: '/admin/tickets', title: '工单管理', icon: 'ChatDotRound' },
]

const activeMenu = computed(() => {
  const hit = menus.find((m) => route.path.startsWith(m.path))
  return hit ? hit.path : ''
})

const displayName = computed(() => authStore.displayName)

async function handleLogout() {
  try {
    await ElMessageBox.confirm('确定退出管理后台吗？', '提示', { type: 'warning' })
  } catch {
    return
  }
  await authStore.logout()
  ElMessage.success('已退出登录')
  router.push('/admin/login')
}
</script>

<template>
  <el-container class="admin-layout">
    <!-- 侧边栏 -->
    <el-aside width="220px" class="admin-aside">
      <div class="aside-brand">
        <div class="brand-logo">Web3</div>
        <div class="brand-sub">管理后台</div>
      </div>
      <el-menu :default-active="activeMenu" router class="aside-menu">
        <el-menu-item v-for="m in menus" :key="m.path" :index="m.path">
          <el-icon><component :is="m.icon" /></el-icon>
          <span>{{ m.title }}</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container class="admin-body">
      <!-- 顶栏 -->
      <el-header class="admin-header">
        <div class="header-title">
          <el-button class="admin-toggle desktop-hidden" text circle aria-label="菜单" @click="drawerVisible = true">
            <el-icon :size="18"><Menu /></el-icon>
          </el-button>
          {{ route.meta.title || '管理后台' }}
        </div>
        <div class="header-right">
          <el-button text class="btn-fore" @click="router.push('/market')">
            <el-icon style="margin-right: 4px"><Back /></el-icon>返回前台
          </el-button>
          <div class="admin-user">
            <span class="avatar-ring">
              <el-avatar :size="24">
                {{ displayName.charAt(0).toUpperCase() }}
              </el-avatar>
            </span>
            <span class="username">{{ displayName }}</span>
          </div>
          <el-button type="danger" plain size="small" @click="handleLogout">
            退出
          </el-button>
        </div>
      </el-header>

      <!-- 移动端抽屉导航 -->
      <el-drawer v-model="drawerVisible" direction="ltr" :size="'240px'" :with-header="false" class="admin-mobile-drawer">
        <div class="aside-brand">
          <div class="brand-logo">Web3</div>
          <div class="brand-sub">管理后台</div>
        </div>
        <el-menu :default-active="activeMenu" router class="aside-menu" @select="drawerVisible = false">
          <el-menu-item v-for="m in menus" :key="m.path" :index="m.path">
            <el-icon><component :is="m.icon" /></el-icon>
            <span>{{ m.title }}</span>
          </el-menu-item>
        </el-menu>
      </el-drawer>

      <el-main class="admin-main">
        <router-view v-slot="{ Component }">
          <transition name="page-fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.admin-layout {
  height: 100vh;
}
.admin-aside {
  background: rgba(10, 14, 23, 0.75);
  border-right: 1px solid var(--glass-border);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  display: flex;
  flex-direction: column;
}
.aside-brand {
  padding: 20px 20px 16px;
}
.brand-logo {
  font-size: 22px;
  font-weight: 800;
  background: var(--accent-grad);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  filter: drop-shadow(0 0 12px rgba(124, 58, 237, 0.45));
}
.brand-sub {
  font-size: 12px;
  color: var(--text-dim);
  margin-top: 2px;
  letter-spacing: 2px;
}
.aside-menu {
  flex: 1;
  border-right: none;
  padding: 0 10px;
}
.aside-menu :deep(.el-menu-item) {
  border-radius: var(--radius-sm);
  margin-bottom: 4px;
  height: 44px;
}
.aside-menu :deep(.el-menu-item.is-active) {
  background: rgba(124, 58, 237, 0.16);
  color: #fff;
  font-weight: 600;
  box-shadow: inset 0 0 0 1px rgba(124, 58, 237, 0.35), var(--glow-purple);
}
.aside-menu :deep(.el-menu-item:hover) {
  background: rgba(255, 255, 255, 0.06);
  color: #fff;
}
.admin-body {
  flex-direction: column;
}
.admin-header {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: rgba(10, 14, 23, 0.6);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border-bottom: 1px solid var(--glass-border);
  padding: 0 24px;
}
.header-title {
  font-size: 16px;
  font-weight: 600;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}
.admin-user {
  display: flex;
  align-items: center;
  gap: 8px;
}
.avatar-ring {
  display: inline-flex;
  padding: 2px;
  border-radius: 50%;
  background: var(--accent-grad);
  box-shadow: var(--glow-purple);
}
.avatar-ring :deep(.el-avatar) {
  background: var(--bg-elevated);
  color: #fff;
  font-size: 13px;
}
.username {
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--text-primary);
}
.admin-main {
  background: transparent;
  padding: 0;
  overflow-y: auto;
}
.page-fade-enter-active,
.page-fade-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}
.page-fade-enter-from {
  opacity: 0;
  transform: translateY(8px);
}
.page-fade-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}

/* ================= 响应式 / 移动端 ================= */
.desktop-hidden {
  display: inline-flex;
}
@media (min-width: 1024px) {
  .desktop-hidden {
    display: none !important;
  }
}
@media (max-width: 1023px) {
  /* 隐藏固定侧边栏，改用抽屉 */
  .admin-aside {
    display: none;
  }
  .admin-header {
    padding: 0 12px;
  }
  .btn-fore {
    display: none !important;
  }
  .admin-user .username {
    display: none;
  }
  .header-title {
    display: flex;
    align-items: center;
    gap: 6px;
  }
  .admin-toggle {
    color: var(--text-primary);
  }
  .admin-main {
    padding: 10px;
  }
  .admin-main .g-card {
    padding: 14px !important;
  }
}
.admin-mobile-drawer {
  background: var(--bg-elevated);
}
</style>
