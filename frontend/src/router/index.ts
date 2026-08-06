import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
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

// 全局路由守卫：未登录跳转 /login
router.beforeEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} - Web3交易所` : 'Web3交易所'
  const authStore = useAuthStore()
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
