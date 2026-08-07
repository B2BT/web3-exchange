<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import * as riskApi from '@/api/risk'
import type { LoginLog, RiskRule } from '@/api/risk'

const authStore = useAuthStore()
const userId = computed(() => authStore.userInfo?.id)

const rules = ref<RiskRule[]>([])
const phishing = ref('')
const logs = ref<LoginLog[]>([])
const logsTotal = ref(0)
const loading = ref(false)
const saving = ref(false)

const phRef = ref<FormInstance>()
const phForm = reactive({ phrase: '' })
const phRules: FormRules = {
  phrase: [
    { required: true, message: '请输入反钓鱼码', trigger: 'blur' },
    { min: 4, max: 64, message: '长度 4-64 字符', trigger: 'blur' },
  ],
}

async function loadAll() {
  if (!userId.value) return
  loading.value = true
  try {
    rules.value = await riskApi.riskRules()
    const ap = await riskApi.riskGetPhishing(userId.value).catch(() => null)
    phishing.value = ap?.phrase ?? ''
    phForm.phrase = ap?.phrase ?? ''
    const d = await riskApi.riskLoginLogs(userId.value, 1, 50)
    logs.value = d?.records ?? []
    logsTotal.value = d?.total ?? 0
  } finally {
    loading.value = false
  }
}

async function savePhishing() {
  if (!userId.value || !phRef.value) return
  const valid = await phRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    const ap = await riskApi.riskSetPhishing(userId.value, phForm.phrase)
    phishing.value = ap?.phrase ?? ''
    ElMessage.success('反钓鱼码已保存')
  } finally {
    saving.value = false
  }
}

function riskTag(r: number | undefined) {
  return r === 1 ? { type: 'danger' as const, label: '异常' } : { type: 'success' as const, label: '正常' }
}
function resultTag(r: number | undefined) {
  return r === 1 ? { type: 'danger' as const, label: '失败' } : { type: 'success' as const, label: '成功' }
}

onMounted(loadAll)
</script>

<template>
  <div class="page-container">
    <el-card shadow="never" class="g-card scan-wrap">
      <template #header>
        <div class="card-header">
          <span>账户安全 / 风控</span>
          <el-button size="small" :loading="loading" @click="loadAll">刷新</el-button>
        </div>
      </template>

      <el-row :gutter="16">
        <!-- 反钓鱼码 -->
        <el-col :span="12" :xs="24" :sm="12">
          <el-card shadow="never" class="g-card sub-card">
            <template #header><span>反钓鱼码 Anti-Phishing</span></template>
            <p class="hint">
              设置后，提现时平台会要求输入该码，用于识别官方通知、防止钓鱼网站冒名。当前：
              <el-tag v-if="phishing" type="success" size="small">{{ phishing }}</el-tag>
              <el-tag v-else type="info" size="small">未设置</el-tag>
            </p>
            <el-form ref="phRef" :model="phForm" :rules="phRules" label-width="80px">
              <el-form-item label="反钓鱼码" prop="phrase">
                <el-input v-model="phForm.phrase" placeholder="4-64 字符自定义短语" maxlength="64" show-word-limit clearable />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" :loading="saving" @click="savePhishing">保存</el-button>
              </el-form-item>
            </el-form>
          </el-card>
        </el-col>

        <!-- 风控规则 -->
        <el-col :span="12" :xs="24" :sm="12">
          <el-card shadow="never" class="g-card sub-card">
            <template #header><span>风控规则</span></template>
            <el-table v-loading="loading" :data="rules" stripe>
              <el-table-column prop="name" label="规则" min-width="180" />
              <el-table-column prop="ruleType" label="类型" width="150" />
              <el-table-column label="阈值" width="120">
                <template #default="{ row }"><span class="num">{{ row.threshold }}</span></template>
              </el-table-column>
              <el-table-column label="状态" width="90">
                <template #default="{ row }">
                  <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
                    {{ row.status === 1 ? '启用' : '停用' }}
                  </el-tag>
                </template>
              </el-table-column>
            </el-table>
          </el-card>
        </el-col>
      </el-row>

      <!-- 登录日志 -->
      <h4 class="section-title">登录日志（异常登录检测）</h4>
      <el-table v-loading="loading" :data="logs" stripe>
        <el-table-column prop="createTime" label="时间" min-width="170" />
        <el-table-column prop="ip" label="IP" width="130" />
        <el-table-column prop="device" label="设备" width="110" />
        <el-table-column label="结果" width="90">
          <template #default="{ row }">
            <el-tag :type="resultTag(row.result).type" size="small">{{ resultTag(row.result).label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="风险" width="90">
          <template #default="{ row }">
            <el-tag :type="riskTag(row.risk).type" size="small">{{ riskTag(row.risk).label }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && !logs.length" description="暂无登录日志" style="padding: 16px 0" />
    </el-card>
  </div>
</template>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.sub-card {
  margin-bottom: 8px;
}
.hint {
  color: var(--text-secondary);
  font-size: 13px;
  margin-bottom: 12px;
  line-height: 1.7;
}
.section-title {
  margin: 24px 0 12px;
  color: var(--text-primary);
}
</style>
