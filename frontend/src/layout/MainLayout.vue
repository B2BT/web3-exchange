<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import { saveLocale } from '@/locales'

const { t, locale } = useI18n()
const authStore = useAuthStore()
const router = useRouter()
const route = useRoute()

// 移动端抽屉导航开关
const drawerVisible = ref(false)
// 语言切换
function switchLocale(loc: string) {
  locale.value = loc
  saveLocale(loc)
}

// 顶栏导航（占位路由，后续 F2/F3 填充）
const menus = [
  { path: '/market', key: 'nav.market' },
  { path: '/trade', key: 'nav.trade' },
  { path: '/asset', key: 'nav.asset' },
  { path: '/margin', key: 'nav.margin' },
  { path: '/staking', key: 'nav.staking' },
  { path: '/wallet', key: 'nav.wallet' },
  { path: '/order', key: 'nav.order' },
  { path: '/notify', key: 'nav.notify' },
  { path: '/risk', key: 'nav.risk' },
  { path: '/api-keys', key: 'nav.apiKeys' },
  { path: '/ticket', key: 'nav.ticket' },
  { path: '/user', key: 'nav.user' },
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
        <!-- 移动端汉堡按钮 -->
        <el-button
          class="menu-toggle desktop-hidden"
          text
          circle
          aria-label="菜单"
          @click="drawerVisible = true"
        >
          <el-icon :size="20"><Menu /></el-icon>
        </el-button>
        <div class="logo" @click="router.push('/market')">Web3 交易所</div>
        <el-menu
          mode="horizontal"
          :default-active="activeMenu"
          :ellipsis="false"
          class="header-menu mobile-hidden"
          router
        >
          <el-menu-item v-for="m in menus" :key="m.path" :index="m.path">
            {{ t(m.key) }}
          </el-menu-item>
        </el-menu>
      </div>
      <div class="header-right">
        <!-- 语言切换 -->
        <el-dropdown @command="switchLocale">
          <span class="lang-toggle">
            <el-icon><Global /></el-icon>
            <span class="lang-label">{{ locale === 'en' ? 'EN' : '中文' }}</span>
            <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="zh">中文</el-dropdown-item>
              <el-dropdown-item command="en">English</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <el-dropdown>
          <span class="user-info">
            <span class="avatar-ring">
              <el-avatar :size="24" :src="userAvatar">
                {{ displayName.charAt(0).toUpperCase() }}
              </el-avatar>
            </span>
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

    <!-- 移动端抽屉导航 -->
    <el-drawer
      v-model="drawerVisible"
      direction="ltr"
      :size="'240px'"
      :with-header="false"
      class="mobile-drawer"
    >
      <div class="drawer-brand">
        <div class="logo" @click="router.push('/market'); drawerVisible = false">Web3 交易所</div>
      </div>
      <el-menu
        :default-active="activeMenu"
        class="drawer-menu"
        router
        @select="drawerVisible = false"
      >
        <el-menu-item v-for="m in menus" :key="m.path" :index="m.path">
          {{ t(m.key) }}
        </el-menu-item>
      </el-menu>
      <div class="drawer-user">
        <el-button type="danger" plain style="width: 100%" @click="handleLogout">
          <el-icon style="margin-right: 4px"><SwitchButton /></el-icon>{{ t('login.logout') }}
        </el-button>
      </div>
    </el-drawer>
    <div class="grad-line header-grad-line" aria-hidden="true"></div>

    <el-main class="layout-main">
      <router-view v-slot="{ Component }">
        <transition name="page-fade" mode="out-in">
          <component :is="Component" />
        </transition>
      </router-view>
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
  /* 玻璃拟态顶栏 */
  background: rgba(10, 14, 23, 0.6);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border-bottom: 1px solid var(--glass-border);
  padding: 0 24px;
  height: 60px;
  position: sticky;
  top: 0;
  z-index: 10;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 32px;
}
.logo {
  font-size: 20px;
  font-weight: 700;
  cursor: pointer;
  white-space: nowrap;
  background: var(--accent-grad);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  filter: drop-shadow(0 0 12px rgba(124, 58, 237, 0.45));
  transition: filter 0.25s;
}
.logo:hover {
  filter: drop-shadow(0 0 20px rgba(34, 211, 238, 0.6));
}
.header-menu {
  border-bottom: none;
  --el-menu-bg-color: transparent;
  --el-menu-text-color: var(--text-secondary);
  --el-menu-active-color: #fff;
  --el-menu-hover-bg-color: transparent;
}
.header-menu :deep(.el-menu-item) {
  border-bottom: none;
  font-size: 14px;
  position: relative;
  transition: color 0.2s;
}
.header-menu :deep(.el-menu-item:hover) {
  color: #fff;
  background: transparent;
}
.header-menu :deep(.el-menu-item.is-active) {
  color: #fff;
  font-weight: 600;
}
/* active 底部渐变指示条 */
.header-menu :deep(.el-menu-item.is-active)::after {
  content: '';
  position: absolute;
  left: 18px;
  right: 18px;
  bottom: 0;
  height: 3px;
  border-radius: 3px 3px 0 0;
  background: var(--accent-grad);
  box-shadow: var(--glow-purple);
  animation: neonEdge 2.5s ease-in-out infinite;
}
.header-grad-line {
  position: sticky;
  top: 60px;
  z-index: 9;
}
.header-right .user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  color: var(--text-primary);
  padding: 4px 8px;
  border-radius: 10px;
  transition: background 0.2s;
}
.header-right .user-info:hover {
  background: rgba(255, 255, 255, 0.06);
}
/* 头像渐变环 */
.avatar-ring {
  display: inline-flex;
  padding: 2px;
  border-radius: 50%;
  background: var(--accent-grad);
  box-shadow: var(--glow-purple);
  flex-shrink: 0;
}
.avatar-ring :deep(.el-avatar) {
  background: var(--bg-elevated);
  color: #fff;
  font-size: 13px;
  border: 1px solid rgba(255, 255, 255, 0.15);
}
.username {
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.layout-main {
  background: transparent;
  padding: 0;
  overflow-y: auto;
}

/* 页面切换：fade + 轻微上移 */
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
/* 移动端隐藏水平菜单、桌面端隐藏汉堡按钮 */
.mobile-hidden {
  display: none;
}
.desktop-hidden {
  display: inline-flex;
}
.menu-toggle {
  color: var(--text-primary);
}
/* 语言切换 */
.lang-toggle {
  display: flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  color: var(--text-secondary);
  padding: 4px 8px;
  border-radius: 10px;
  transition: background 0.2s;
}
.lang-toggle:hover {
  background: rgba(255, 255, 255, 0.06);
  color: var(--text-primary);
}
.lang-label {
  font-size: 13px;
  font-weight: 600;
}
@media (min-width: 1024px) {
  .mobile-hidden {
    display: flex;
  }
  .desktop-hidden {
    display: none !important;
  }
}
@media (max-width: 1023px) {
  .layout-header {
    padding: 0 12px;
  }
  .logo {
    font-size: 18px;
  }
  .username {
    display: none; /* 小屏隐藏用户名，只留头像 */
  }
}
/* 移动端抽屉样式 */
.mobile-drawer {
  background: var(--bg-elevated);
}
.drawer-brand {
  padding: 20px 16px 8px;
}
.drawer-menu {
  border-right: none;
  background: transparent;
}
.drawer-menu :deep(.el-menu-item) {
  border-radius: 8px;
  margin: 2px 8px;
  height: 44px;
}
.drawer-menu :deep(.el-menu-item.is-active) {
  background: rgba(124, 58, 237, 0.16);
  color: #fff;
}
.drawer-user {
  position: absolute;
  bottom: 24px;
  left: 0;
  right: 0;
  padding: 0 20px;
}
</style>
