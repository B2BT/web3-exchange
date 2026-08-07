<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import * as ticketApi from '@/api/ticket'
import { TICKET_CATEGORIES, TICKET_STATUS, type Ticket, type TicketDetail, type TicketReply } from '@/api/ticket'

const list = ref<Ticket[]>([])
const total = ref(0)
const loading = ref(false)
const page = ref(1)
const size = ref(20)
const statusFilter = ref<number | undefined>(undefined)

// 新建工单
const dlgVisible = ref(false)
const saving = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({ category: 'DEPOSIT', title: '', content: '', priority: 1 })
const rules: FormRules = {
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  content: [{ required: true, message: '请输入问题描述', trigger: 'blur' }],
}

// 详情
const detailVisible = ref(false)
const detail = ref<TicketDetail | null>(null)
const replyText = ref('')
const replySaving = ref(false)

const PRIORITY_MAP: Record<number, string> = { 0: '低', 1: '中', 2: '高' }

async function load() {
  loading.value = true
  try {
    const d = await ticketApi.myTickets(page.value, size.value, statusFilter.value)
    list.value = d?.records ?? []
    total.value = d?.total ?? 0
  } finally {
    loading.value = false
  }
}

function openCreate() {
  form.category = 'DEPOSIT'
  form.title = ''
  form.content = ''
  form.priority = 1
  dlgVisible.value = true
}

async function save() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    await ticketApi.createTicket({ ...form })
    ElMessage.success('工单已提交')
    dlgVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function openDetail(row: Ticket) {
  detail.value = await ticketApi.ticketDetail(row.id!)
  replyText.value = ''
  detailVisible.value = true
}

async function sendReply() {
  if (!replyText.value.trim()) return ElMessage.warning('请输入回复内容')
  replySaving.value = true
  try {
    await ticketApi.replyTicket(detail.value!.id!, replyText.value.trim())
    ElMessage.success('已回复')
    detail.value = await ticketApi.ticketDetail(detail.value!.id!)
    replyText.value = ''
  } finally {
    replySaving.value = false
  }
}

async function close(row: Ticket) {
  await ticketApi.closeTicket(row.id!)
  ElMessage.success('工单已关闭')
  load()
}

function statusTag(s: number | undefined) {
  return s === 0 ? { type: 'warning' as const, label: '待处理' }
    : s === 1 ? { type: 'primary' as const, label: '处理中' }
    : s === 2 ? { type: 'success' as const, label: '已解决' }
    : { type: 'info' as const, label: '已关闭' }
}
function catLabel(c: string | undefined) {
  return TICKET_CATEGORIES.find((x) => x.value === c)?.label || c || '-'
}
function isStaff(r: TicketReply) {
  return r.isStaff === 1
}

onMounted(load)
</script>

<template>
  <div class="page-container">
    <el-card shadow="never" class="g-card scan-wrap">
      <template #header>
        <div class="head">
          <span>客服工单</span>
          <el-button type="primary" size="small" @click="openCreate">提交工单</el-button>
        </div>
      </template>

      <div class="toolbar">
        <el-select v-model="statusFilter" placeholder="全部状态" clearable style="width: 140px" @change="page = 1; load()">
          <el-option v-for="(label, val) in TICKET_STATUS" :key="val" :label="label" :value="Number(val)" />
        </el-select>
        <el-button @click="load">刷新</el-button>
      </div>

      <el-table v-loading="loading" :data="list" stripe>
        <el-table-column prop="title" label="标题" min-width="180" />
        <el-table-column label="分类" width="80">
          <template #default="{ row }">{{ catLabel(row.category) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status).type" size="small">{{ statusTag(row.status).label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="优先级" width="80">
          <template #default="{ row }">{{ PRIORITY_MAP[row.priority] }}</template>
        </el-table-column>
        <el-table-column prop="createTime" label="提交时间" min-width="160" />
        <el-table-column label="操作" width="180">
          <template #default="{ row }">
            <el-button size="small" @click="openDetail(row)">查看/回复</el-button>
            <el-button v-if="row.status !== 3" size="small" type="warning" @click="close(row)">关闭</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :total="total"
        layout="total, prev, pager, next"
        style="margin-top: 16px; justify-content: flex-end"
        @change="load"
      />
    </el-card>

    <!-- 新建工单 -->
    <el-dialog v-model="dlgVisible" title="提交工单" width="520px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="70px">
        <el-form-item label="分类">
          <el-select v-model="form.category" style="width: 100%">
            <el-option v-for="c in TICKET_CATEGORIES" :key="c.value" :label="c.label" :value="c.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="优先级">
          <el-radio-group v-model="form.priority">
            <el-radio-button :value="0">低</el-radio-button>
            <el-radio-button :value="1">中</el-radio-button>
            <el-radio-button :value="2">高</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" placeholder="简要描述问题" />
        </el-form-item>
        <el-form-item label="描述" prop="content">
          <el-input v-model="form.content" type="textarea" :rows="5" placeholder="详细描述问题，如需附交易哈希/地址请一并填写" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlgVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">提交</el-button>
      </template>
    </el-dialog>

    <!-- 工单详情 -->
    <el-dialog v-model="detailVisible" :title="detail?.title" width="560px">
      <div v-if="detail">
        <div class="detail-meta">
          <el-tag size="small">{{ catLabel(detail.category) }}</el-tag>
          <el-tag size="small" :type="statusTag(detail.status).type">{{ statusTag(detail.status).label }}</el-tag>
          <span class="dim">提交于 {{ detail.createTime }}</span>
        </div>
        <div class="detail-content">{{ detail.content }}</div>

        <div class="reply-list">
          <div v-for="r in detail.replies" :key="r.id" class="reply-item" :class="{ staff: isStaff(r) }">
            <div class="reply-head">
              <span class="reply-name">{{ isStaff(r) ? '客服' : '我' }}</span>
              <span class="dim">{{ r.createTime }}</span>
            </div>
            <div class="reply-body">{{ r.content }}</div>
          </div>
        </div>

        <div class="reply-input">
          <el-input v-model="replyText" type="textarea" :rows="3" placeholder="输入回复内容..." />
          <el-button type="primary" style="margin-top: 8px" :loading="replySaving" @click="sendReply">回复</el-button>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.toolbar {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 16px;
}
.detail-meta {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 12px;
}
.detail-content {
  padding: 12px;
  background: var(--glass-bg);
  border: 1px solid var(--glass-border);
  border-radius: 8px;
  margin-bottom: 16px;
  color: var(--text-primary);
}
.reply-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 16px;
}
.reply-item {
  padding: 10px 12px;
  border-radius: 8px;
  background: var(--glass-bg);
  border: 1px solid var(--glass-border);
}
.reply-item.staff {
  border-color: rgba(34, 197, 94, 0.4);
  background: rgba(34, 197, 94, 0.06);
}
.reply-head {
  display: flex;
  justify-content: space-between;
  margin-bottom: 6px;
}
.reply-name {
  font-weight: 600;
  color: var(--text-primary);
}
.reply-item.staff .reply-name {
  color: var(--up);
}
.dim {
  color: var(--text-dim);
  font-size: 12px;
}
</style>
