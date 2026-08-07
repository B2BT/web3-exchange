<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import * as adminApi from '@/api/adminB'
import type { Announcement } from '@/api/adminB'

const list = ref<Announcement[]>([])
const total = ref(0)
const loading = ref(false)
const page = ref(1)
const size = ref(20)
const keyword = ref('')
const saving = ref(false)

const dlgVisible = ref(false)
const editingId = ref<string | number | null>(null)
const formRef = ref<FormInstance>()
const form = reactive({ title: '', content: '', type: 0 })
const rules: FormRules = {
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  content: [{ required: true, message: '请输入内容', trigger: 'blur' }],
}

async function load() {
  loading.value = true
  try {
    const d = await adminApi.adminAnnouncements(page.value, size.value, keyword.value || undefined)
    list.value = d?.records ?? []
    total.value = d?.total ?? 0
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = null
  form.title = ''
  form.content = ''
  form.type = 0
  dlgVisible.value = true
}
function openEdit(row: Announcement) {
  editingId.value = row.id ?? null
  form.title = row.title ?? ''
  form.content = row.content ?? ''
  form.type = row.type ?? 0
  dlgVisible.value = true
}

async function save() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    if (editingId.value != null) {
      await adminApi.adminAnnouncementUpdate({ id: editingId.value, ...form })
    } else {
      await adminApi.adminAnnouncementCreate(form)
    }
    ElMessage.success('保存成功')
    dlgVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function publish(row: Announcement) {
  await adminApi.adminAnnouncementPublish(row.id!, row.status !== 1)
  ElMessage.success(row.status === 1 ? '已下线' : '已发布')
  load()
}
async function remove(row: Announcement) {
  await adminApi.adminAnnouncementDelete(row.id!)
  ElMessage.success('已删除')
  load()
}

function statusTag(s: number | undefined) {
  return s === 1 ? { type: 'success' as const, label: '已发布' } : s === 2 ? { type: 'info' as const, label: '已下线' } : { type: 'warning' as const, label: '草稿' }
}
function typeLabel(t: number | undefined) {
  return t === 1 ? '活动' : t === 2 ? '系统' : '公告'
}

onMounted(load)
</script>

<template>
  <div class="admin-page g-card scan-wrap">
    <div class="toolbar">
      <el-input v-model="keyword" placeholder="搜索标题" clearable style="width: 220px" @keyup.enter="page = 1; load()" />
      <el-button @click="page = 1; load()">搜索</el-button>
      <div style="flex: 1"></div>
      <el-button type="primary" @click="openCreate">新建公告</el-button>
    </div>

    <el-table v-loading="loading" :data="list" stripe>
      <el-table-column prop="title" label="标题" min-width="200" />
      <el-table-column label="类型" width="80">
        <template #default="{ row }">{{ typeLabel(row.type) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="statusTag(row.status).type" size="small">{{ statusTag(row.status).label }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="viewCount" label="浏览" width="80" />
      <el-table-column prop="publishTime" label="发布时间" min-width="160" />
      <el-table-column label="操作" width="220">
        <template #default="{ row }">
          <el-button size="small" @click="openEdit(row)">编辑</el-button>
          <el-button size="small" :type="row.status === 1 ? 'info' : 'success'" @click="publish(row)">
            {{ row.status === 1 ? '下线' : '发布' }}
          </el-button>
          <el-button size="small" type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="page"
      v-model:page-size="size"
      :total="total"
      :page-sizes="[10, 20, 50]"
      layout="total, sizes, prev, pager, next"
      style="margin-top: 16px; justify-content: flex-end"
      @change="load"
    />

    <el-dialog v-model="dlgVisible" :title="editingId != null ? '编辑公告' : '新建公告'" width="560px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="60px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" placeholder="公告标题" />
        </el-form-item>
        <el-form-item label="类型">
          <el-radio-group v-model="form.type">
            <el-radio-button :value="0">公告</el-radio-button>
            <el-radio-button :value="1">活动</el-radio-button>
            <el-radio-button :value="2">系统</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="内容" prop="content">
          <el-input v-model="form.content" type="textarea" :rows="6" placeholder="公告内容" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlgVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.admin-page {
  padding: 20px;
}
.toolbar {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 16px;
}
</style>
