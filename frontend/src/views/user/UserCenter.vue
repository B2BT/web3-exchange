<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useAuthStore } from '@/stores/auth'
import * as userApi from '@/api/user'
import type { UserProfile, KycStatus } from '@/api/user'

const authStore = useAuthStore()

const userId = computed(() => authStore.userInfo?.id)
const username = computed(() => authStore.userInfo?.username)

const profile = ref<UserProfile | null>(null)
const kyc = ref<KycStatus | null>(null)
const level = ref<string>('')
const loading = ref(false)

const KYC_MAP: Record<number, string> = { 0: '未认证', 1: '审核中', 2: '已认证', 3: '已拒绝' }
const KYC_LEVEL_MAP: Record<number, string> = { 0: '-', 1: 'L1', 2: 'L2', 3: 'L3' }
const LEVEL_MAP: Record<string, string> = { NORMAL: '普通', VIP: 'VIP', SVIP: 'SVIP' }

async function loadProfile() {
  if (!userId.value || !username.value) return
  loading.value = true
  try {
    profile.value = await userApi.getUserInfo(username.value)
  } catch {
    profile.value = null
  } finally {
    loading.value = false
  }
}

async function loadExtra() {
  if (!userId.value) return
  try {
    kyc.value = await userApi.getKycStatus(userId.value)
  } catch {
    kyc.value = null
  }
  try {
    level.value = await userApi.getUserLevel(userId.value)
  } catch {
    level.value = ''
  }
}

onMounted(() => {
  loadProfile()
  loadExtra()
})
</script>

<template>
  <div class="page-container">
    <el-card shadow="never" class="g-card" v-loading="loading">
      <template #header>用户中心</template>

      <div class="profile-head">
        <span class="profile-avatar-ring">
          <el-avatar :size="60" :src="profile?.avatar || authStore.userInfo?.avatar">
            {{ (profile?.nickname || authStore.displayName || 'U').charAt(0).toUpperCase() }}
          </el-avatar>
        </span>
        <div>
          <div class="nickname">{{ profile?.nickname || authStore.displayName }}</div>
          <div class="username">@{{ profile?.username || authStore.userInfo?.username }}</div>
        </div>
      </div>

      <el-descriptions :column="2" border style="margin-top: 20px">
        <el-descriptions-item label="用户ID">{{ profile?.id ?? authStore.userInfo?.id ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="用户名">{{ profile?.username ?? authStore.userInfo?.username ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="昵称">{{ profile?.nickname ?? authStore.userInfo?.nickname ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="真实姓名">{{ profile?.realName ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="邮箱">{{ profile?.email ?? authStore.userInfo?.email ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="手机号">{{ profile?.phone ?? authStore.userInfo?.phone ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="用户等级">
          <el-tag size="small" type="warning">
            {{ LEVEL_MAP[level] || level || authStore.userInfo?.roles?.[0] || '-' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="KYC 认证">
          <el-tag :type="kyc?.kycStatus === 2 ? 'success' : kyc?.kycStatus === 1 ? 'warning' : 'info'" size="small">
            {{ kyc ? (KYC_MAP[kyc.kycStatus ?? 0] ?? kyc.kycStatus) : '-' }}
          </el-tag>
          <span v-if="kyc?.kycLevel" class="kyc-level">等级 {{ KYC_LEVEL_MAP[kyc.kycLevel] ?? kyc.kycLevel }}</span>
        </el-descriptions-item>
      </el-descriptions>

      <el-alert
        type="warning"
        :closable="false"
        style="margin-top: 20px"
        title="2FA / 改密 / KYC 提交等安全操作"
        description="当前阶段用户中心以资料展示为主。2FA 开启、密码修改、KYC 实名提交等安全流程将在后续迭代接入（后端接口已就绪）。"
      />
    </el-card>
  </div>
</template>

<style scoped>
.profile-head {
  display: flex;
  align-items: center;
  gap: 16px;
}
/* 头像渐变环 */
.profile-avatar-ring {
  display: inline-flex;
  padding: 3px;
  border-radius: 50%;
  background: var(--accent-grad);
  box-shadow: var(--glow-purple);
  flex-shrink: 0;
}
.profile-avatar-ring :deep(.el-avatar) {
  background: var(--bg-elevated);
  color: #fff;
  border: 1px solid rgba(255, 255, 255, 0.15);
}
.nickname {
  font-size: 20px;
  font-weight: 700;
  color: var(--text-primary);
}
.username {
  color: var(--text-secondary);
  font-size: 13px;
}
.kyc-level {
  margin-left: 8px;
  font-size: 12px;
  color: var(--text-secondary);
}
</style>
