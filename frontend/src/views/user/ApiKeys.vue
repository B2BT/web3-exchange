<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import * as apiKeyApi from '@/api/apiKey'
import type { ApiKeyItem } from '@/api/apiKey'

const { t } = useI18n()

const list = ref<ApiKeyItem[]>([])
const loading = ref(false)
const dlgVisible = ref(false)
const saving = ref(false)
const form = reactive({ label: '', permission: 'READ,TRADE' })
/** 创建成功后的一次性明文 Secret 展示 */
const justCreated = ref<ApiKeyItem | null>(null)

const PERMISSION_OPTIONS = [
  { label: '只读', value: 'READ' },
  { label: '交易', value: 'READ,TRADE' },
  { label: '交易+提现', value: 'READ,TRADE,WITHDRAW' },
]

async function load() {
  loading.value = true
  try {
    list.value = await apiKeyApi.listApiKeys()
  } finally {
    loading.value = false
  }
}

function openCreate() {
  form.label = ''
  form.permission = 'READ,TRADE'
  justCreated.value = null
  dlgVisible.value = true
}

async function save() {
  saving.value = true
  try {
    justCreated.value = await apiKeyApi.createApiKey({ label: form.label, permission: form.permission })
    ElMessage.success('密钥创建成功，请立即保存 Secret（仅显示一次）')
    dlgVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function toggle(row: ApiKeyItem) {
  await apiKeyApi.toggleApiKey(row.id!, row.status !== 1)
  ElMessage.success(row.status === 1 ? '已停用' : '已启用')
  load()
}
async function remove(row: ApiKeyItem) {
  await apiKeyApi.deleteApiKey(row.id!)
  ElMessage.success('已删除')
  load()
}

function statusTag(s: number | undefined) {
  return s === 1 ? { type: 'success' as const, label: '启用' } : { type: 'danger' as const, label: '停用' }
}

onMounted(load)
</script>

<template>
  <div class="page-container">
    <el-card shadow="never" class="g-card scan-wrap">
      <template #header>
        <div class="head">
          <span>{{ t('apiKeys.title') }}</span>
          <el-button type="primary" size="small" @click="openCreate">{{ t('apiKeys.create') }}</el-button>
        </div>
      </template>

      <!-- 创建成功后明文 Secret 展示（仅一次） -->
      <el-alert v-if="justCreated" type="success" :closable="false" class="secret-alert">
        <template #title>
          <div class="secret-title">密钥创建成功！Secret 仅显示这一次，请立即复制保存：</div>
          <div class="secret-block">
            <div><span class="dim">API Key：</span><code class="mono">{{ justCreated.apiKey }}</code></div>
            <div><span class="dim">Secret Key：</span><code class="mono warn">{{ justCreated.secretKey }}</code></div>
          </div>
        </template>
      </el-alert>

      <el-alert type="info" :closable="false" show-icon style="margin-bottom: 16px"
        title="API 密钥用于程序化交易 / 量化机器人调用行情与交易接口。Secret 仅创建时显示一次，落库加密存储，请妥善保管。提现权限默认关闭，如需请重新创建。" />

      <el-table v-loading="loading" :data="list" stripe>
        <el-table-column prop="label" :label="t('apiKeys.label')" width="120" />
        <el-table-column prop="apiKey" label="API Key" min-width="240" show-overflow-tooltip />
        <el-table-column prop="secretKey" label="Secret" width="80">
          <template #default>***</template>
        </el-table-column>
        <el-table-column :label="t('apiKeys.permission')" width="150">
          <template #default="{ row }">
            <el-tag v-for="p in (row.permission || '').split(',')" :key="p" size="small" style="margin-right: 4px">{{ p }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status).type" size="small">{{ statusTag(row.status).label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" min-width="160" />
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-button size="small" :type="row.status === 1 ? 'warning' : 'success'" @click="toggle(row)">
              {{ row.status === 1 ? '停用' : '启用' }}
            </el-button>
            <el-button size="small" type="danger" @click="remove(row)">{{ t('apiKeys.delete') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && !list.length" description="暂无 API 密钥，点击右上角创建" />
    </el-card>

    <el-dialog v-model="dlgVisible" :title="t('apiKeys.create')" width="460px">
      <el-form label-width="70px">
        <el-form-item :label="t('apiKeys.label')">
          <el-input v-model="form.label" :placeholder="t('apiKeys.label')" />
        </el-form-item>
        <el-form-item :label="t('apiKeys.permission')">
          <el-select v-model="form.permission" style="width: 100%">
            <el-option v-for="o in PERMISSION_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlgVisible = false">{{ t('apiKeys.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="save">{{ t('apiKeys.createConfirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.secret-alert {
  margin-bottom: 16px;
}
.secret-title {
  font-weight: 600;
}
.secret-block {
  margin-top: 8px;
  line-height: 2;
}
.mono {
  font-family: var(--font-num), monospace;
  background: rgba(255, 255, 255, 0.06);
  padding: 2px 8px;
  border-radius: 4px;
  word-break: break-all;
}
.mono.warn {
  color: var(--down);
}
.dim {
  color: var(--text-dim);
}
</style>
