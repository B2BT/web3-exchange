<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import * as adminApi from '@/api/adminB'
import type { AdminSymbol } from '@/api/adminB'

const list = ref<AdminSymbol[]>([])
const total = ref(0)
const loading = ref(false)
const page = ref(1)
const size = ref(20)
const keyword = ref('')
const saving = ref(false)

const dlgVisible = ref(false)
const editingId = ref<string | number | null>(null)
const formRef = ref<FormInstance>()
const form = reactive({
  symbol: '', baseCoin: '', quoteCoin: '',
  pricePrecision: 8, amountPrecision: 8, priceTick: 1,
  minAmount: 0, maxAmount: 0,
  minNotional: 0,
  takerFeeRate: 0, makerFeeRate: 0, sort: 0,
})
const rules: FormRules = {
  symbol: [{ required: true, message: '请输入交易对', trigger: 'blur' }],
  baseCoin: [{ required: true, message: '基础币', trigger: 'blur' }],
  quoteCoin: [{ required: true, message: '计价币', trigger: 'blur' }],
}

async function load() {
  loading.value = true
  try {
    const d = await adminApi.adminSymbols(page.value, size.value, keyword.value || undefined)
    list.value = d?.records ?? []
    total.value = d?.total ?? 0
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = null
  Object.assign(form, { symbol: '', baseCoin: '', quoteCoin: '', pricePrecision: 8, amountPrecision: 8, priceTick: 1, minAmount: 0, maxAmount: 0, minNotional: 0, takerFeeRate: 0, makerFeeRate: 0, sort: 0 })
  dlgVisible.value = true
}
function openEdit(row: AdminSymbol) {
  editingId.value = row.id ?? null
  Object.assign(form, {
    symbol: row.symbol ?? '', baseCoin: row.baseCoin ?? '', quoteCoin: row.quoteCoin ?? '',
    pricePrecision: row.pricePrecision ?? 8, amountPrecision: row.amountPrecision ?? 8, priceTick: row.priceTick ?? 1,
    minAmount: row.minAmount ?? 0, maxAmount: row.maxAmount ?? 0, minNotional: row.minNotional ?? 0,
    takerFeeRate: row.takerFeeRate ?? 0, makerFeeRate: row.makerFeeRate ?? 0, sort: row.sort ?? 0,
  })
  dlgVisible.value = true
}

async function save() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    if (editingId.value != null) {
      await adminApi.adminSymbolUpdate({ id: editingId.value, ...form })
    } else {
      await adminApi.adminSymbolCreate(form)
    }
    ElMessage.success('保存成功')
    dlgVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function toggle(row: AdminSymbol) {
  await adminApi.adminSymbolToggle(row.id!, row.status !== 1)
  ElMessage.success(row.status === 1 ? '已停牌' : '已上牌')
  load()
}

onMounted(load)
</script>

<template>
  <div class="admin-page g-card">
    <div class="toolbar">
      <el-input v-model="keyword" placeholder="搜索交易对" clearable style="width: 220px" @keyup.enter="page = 1; load()" />
      <el-button @click="page = 1; load()">搜索</el-button>
      <div style="flex: 1"></div>
      <el-button type="primary" @click="openCreate">新增交易对</el-button>
    </div>

    <el-table v-loading="loading" :data="list" stripe>
      <el-table-column prop="symbol" label="交易对" min-width="130" />
      <el-table-column prop="baseCoin" label="基础币" width="90" />
      <el-table-column prop="quoteCoin" label="计价币" width="90" />
      <el-table-column label="精度" width="90">
        <template #default="{ row }">P{{ row.pricePrecision }}/A{{ row.amountPrecision }}</template>
      </el-table-column>
      <el-table-column label="最小名义值" width="110">
        <template #default="{ row }"><span class="num">{{ row.minNotional }}</span></template>
      </el-table-column>
      <el-table-column label="吃单费率" width="90">
        <template #default="{ row }"><span class="num">{{ row.takerFeeRate }}</span>bp</template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
            {{ row.status === 1 ? '交易中' : '停牌' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="170">
        <template #default="{ row }">
          <el-button size="small" @click="openEdit(row)">编辑</el-button>
          <el-button size="small" :type="row.status === 1 ? 'warning' : 'success'" @click="toggle(row)">
            {{ row.status === 1 ? '停牌' : '上牌' }}
          </el-button>
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

    <el-dialog v-model="dlgVisible" :title="editingId != null ? '编辑交易对' : '新增交易对'" width="600px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="交易对" prop="symbol"><el-input v-model="form.symbol" :disabled="editingId != null" placeholder="BTC/USDT" /></el-form-item></el-col>
          <el-col :span="6"><el-form-item label="基础币" prop="baseCoin"><el-input v-model="form.baseCoin" :disabled="editingId != null" /></el-form-item></el-col>
          <el-col :span="6"><el-form-item label="计价币" prop="quoteCoin"><el-input v-model="form.quoteCoin" :disabled="editingId != null" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="8"><el-form-item label="价格精度"><el-input-number v-model="form.pricePrecision" :min="0" :max="18" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="数量精度"><el-input-number v-model="form.amountPrecision" :min="0" :max="18" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="价格步长"><el-input-number v-model="form.priceTick" :min="1" style="width: 100%" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="8"><el-form-item label="最小数量"><el-input-number v-model="form.minAmount" :min="0" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="最大数量"><el-input-number v-model="form.maxAmount" :min="0" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="最小名义值"><el-input-number v-model="form.minNotional" :min="0" style="width: 100%" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="8"><el-form-item label="吃单费率bp"><el-input-number v-model="form.takerFeeRate" :min="0" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="挂单费率bp"><el-input-number v-model="form.makerFeeRate" :min="0" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="排序"><el-input-number v-model="form.sort" style="width: 100%" /></el-form-item></el-col>
        </el-row>
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
