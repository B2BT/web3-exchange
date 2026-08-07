import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/Login.vue'),
    meta: { title: '登录', public: true },
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/register/Register.vue'),
    meta: { title: '注册', public: true },
  },
  {
    path: '/admin/login',
    name: 'AdminLogin',
    component: () => import('@/views/admin/login/AdminLogin.vue'),
    meta: { title: '管理员登录', public: true, admin: true, adminLogin: true },
  },
  {
    path: '/admin',
    component: () => import('@/views/admin/layout/AdminLayout.vue'),
    meta: { title: '管理后台', admin: true },
    redirect: '/admin/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'AdminDashboard',
        component: () => import('@/views/admin/dashboard/AdminDashboard.vue'),
        meta: { title: '仪表盘', admin: true },
      },
      {
        path: 'users',
        name: 'AdminUsers',
        component: () => import('@/views/admin/users/AdminUsers.vue'),
        meta: { title: '用户管理', admin: true },
      },
      {
        path: 'orders',
        name: 'AdminOrders',
        component: () => import('@/views/admin/orders/AdminOrders.vue'),
        meta: { title: '订单管理', admin: true },
      },
      {
        path: 'withdrawals',
        name: 'AdminWithdrawals',
        component: () => import('@/views/admin/withdrawals/AdminWithdrawals.vue'),
        meta: { title: '提现审核', admin: true },
      },
    ],
  },
  {
    path: '/',
    component: () => import('@/layout/MainLayout.vue'),
    redirect: '/market',
    children: [
      {
        path: 'market',
        name: 'Market',
        component: () => import('@/views/market/Market.vue'),
        meta: { title: '行情' },
      },
      {
        path: 'trade',
        name: 'Trade',
        component: () => import('@/views/trade/Trade.vue'),
        meta: { title: '交易' },
      },
      {
        path: 'asset',
        name: 'Asset',
        component: () => import('@/views/asset/Asset.vue'),
        meta: { title: '资产' },
      },
      {
        path: 'wallet',
        name: 'Wallet',
        component: () => import('@/views/wallet/Wallet.vue'),
        meta: { title: 'Web3钱包' },
      },
      {
        path: 'margin',
        name: 'Margin',
        component: () => import('@/views/margin/Margin.vue'),
        meta: { title: '杠杆' },
      },
      {
        path: 'order',
        name: 'Order',
        component: () => import('@/views/order/Order.vue'),
        meta: { title: '订单' },
      },
      {
        path: 'notify',
        name: 'Notify',
        component: () => import('@/views/notify/Notify.vue'),
        meta: { title: '通知' },
      },
      {
        path: 'user',
        name: 'UserCenter',
        component: () => import('@/views/user/UserCenter.vue'),
        meta: { title: '用户中心' },
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/market',
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

// 全局路由守卫：未登录跳转 /login；管理后台校验 role=ADMIN
router.beforeEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} - Web3交易所` : 'Web3交易所'
  const authStore = useAuthStore()
  const isAdmin = authStore.userInfo?.role === 'ADMIN'

  // 管理后台路由
  if (to.meta.admin) {
    // 管理员登录页：已登录 ADMIN 直接进后台
    if (to.meta.adminLogin) {
      if (isAdmin) return { path: '/admin/dashboard' }
      return true
    }
    // 其余后台页：必须 ADMIN
    if (!isAdmin) {
      ElMessage.warning('需要管理员权限才能访问管理后台')
      return { path: '/admin/login', query: { redirect: to.fullPath } }
    }
    return true
  }

  const requiresAuth = !to.meta.public
  if (requiresAuth && !authStore.isLoggedIn) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  // 已登录访问 login/register 则跳转行情页
  if (to.meta.public && authStore.isLoggedIn && (to.path === '/login' || to.path === '/register')) {
    return { path: '/market' }
  }
  return true
})

export default router
