import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import * as authApi from '@/api/auth'
import type { LoginRequest, RegisterRequest, UserInfo } from '@/api/types'

const TOKEN_KEY = 'web3_exchange_access_token'
const REFRESH_TOKEN_KEY = 'web3_exchange_refresh_token'
const USER_KEY = 'web3_exchange_user'

/**
 * 认证状态：token/userInfo 持久化 localStorage
 */
export const useAuthStore = defineStore('auth', () => {
  const accessToken = ref<string>(localStorage.getItem(TOKEN_KEY) || '')
  const refreshToken = ref<string>(localStorage.getItem(REFRESH_TOKEN_KEY) || '')
  const userInfo = ref<UserInfo | null>(
    JSON.parse(localStorage.getItem(USER_KEY) || 'null'),
  )

  const isLoggedIn = computed(() => !!accessToken.value)
  const displayName = computed(
    () => userInfo.value?.nickname || userInfo.value?.username || '用户',
  )

  function persist() {
    localStorage.setItem(TOKEN_KEY, accessToken.value)
    if (refreshToken.value) {
      localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken.value)
    } else {
      localStorage.removeItem(REFRESH_TOKEN_KEY)
    }
    localStorage.setItem(USER_KEY, JSON.stringify(userInfo.value || null))
  }

  /** 登录：成功后存 token/userInfo */
  async function login(payload: LoginRequest) {
    const data = await authApi.login(payload)
    accessToken.value = data.accessToken
    refreshToken.value = data.refreshToken || ''
    userInfo.value = data.userInfo || null
    persist()
    return data
  }

  /** 注册 */
  async function register(payload: RegisterRequest) {
    return authApi.register(payload)
  }

  /** 加载验证码 */
  async function fetchCaptcha() {
    return authApi.getCaptcha()
  }

  /** 登出：清空本地 + 通知后端 */
  async function logout() {
    const rt = refreshToken.value
    try {
      if (accessToken.value) {
        await authApi.logout(rt || undefined)
      }
    } catch {
      // 忽略登出接口错误，本地一定清理
    }
    clearAuth()
  }

  /** 仅清空本地认证状态（401 时使用） */
  function clearAuth() {
    accessToken.value = ''
    refreshToken.value = ''
    userInfo.value = null
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(REFRESH_TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
  }

  return {
    accessToken,
    refreshToken,
    userInfo,
    isLoggedIn,
    displayName,
    login,
    register,
    fetchCaptcha,
    logout,
    clearAuth,
  }
})
