<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminWithdrawList, adminWithdrawAudit, type AdminWithdrawItem } from '@/api/admin'
import { formatLong } from '@/utils/format'
import { coinDecimals } from '@/config/market'

const WITHDRAW_STATUS: Record<number, { text: string; type: string }> = {
  0: { text: '待审核', type: 'warning' },
  1: { text: '审核中', type: 'primary' },
  2: { text: '处理中', type: 'primary' },
  3: { text: '成功', type: 'success' },
  4: { text: '拒绝', type: 'danger' },
  5: { text: '失败回滚', type: 'danger' },
}
const STATUS_OPTIONS = Object.entries(WITHDRAW_STATUS).map(([k, v]) => ({
  value: Number(k),
  label: v.text,
}))

const list = ref<AdminWithdrawItem[]>([])
const loading = ref(false)
const total = ref(0)
const page = ref(1)
const size = ref(20)
const status = ref<number | ''>('')

// 审核弹窗
const dialogVisible = ref(false)
const auditLoading = ref(false)
const auditRow = ref<AdminWithdrawItem | null>(null)
const auditForm = reactive({ approved: true, remark: '' })

async function load(p = page.value) {
  loading.value = true
  page.value = p
  try {
    const res = await adminWithdrawList({
      page: page.value,
      size: size.value,
      status: status.value === '' ? undefined : status.value,
    })
    list.value = res.records || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  load(1)
}

function openAudit(row: AdminWithdrawItem, approved: boolean) {
  auditRow.value = row
  auditForm.approved = approved
  auditForm.remark = ''
  dialogVisible.value = true
}

async function submitAudit() {
  if (!auditRow.value?.id) return
  auditLoading.value = true
  try {
    await adminWithdrawAudit(auditRow.value.id, {
      approved: auditForm.approved,
      remark: auditForm.remark,
    })
    ElMessage.success(auditForm.approved ? '已通过该笔提现' : '已拒绝该笔提现')
    dialogVisible.value = false
    load(page.value)
  } catch {
    // 错误已由拦截器提示
  } finally {
    auditLoading.value = false
  }
}

onMounted(() => load(1))
</script>

<template>
  <div class="page-container">
    <el-card shadow="never" class="g-card">
      <template #header>
        <div class="card-header">
          <span>提现审核</span>
          <div class="search-row">
            <el-select
              v-model="status"
              placeholder="提现状态"
              clearable
              style="width: 150px"
              @change="handleSearch"
            >
              <el-option v-for="opt in STATUS_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
            </el-select>
            <el-button type="primary" @click="handleSearch">查询</el-button>
          </div>
        </div>
      </template>

      <el-table v-loading="loading" :data="list" stripe>
        <el-table-column prop="username" label="用户" min-width="100">
          <template #default="{ row }">{{ row.username || '-' }}</template>
        </el-table-column>
        <el-table-column prop="symbol" label="币种" width="80" />
        <el-table-column prop="chainCode" label="链" width="80" />
        <el-table-column label="金额" min-width="140">
          <template #default="{ row }">
            <span class="num">{{ formatLong(row.amount, coinDecimals(row.symbol)) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="手续费" min-width="110">
          <template #default="{ row }">
            <span class="num">{{ formatLong(row.fee, coinDecimals(row.symbol)) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="toAddress" label="目标地址" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ row.toAddress || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="(WITHDRAW_STATUS[row.status || 0] || {}).type" size="small">
              {{ (WITHDRAW_STATUS[row.status || 0] || {}).text || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="审核备注" min-width="130" show-overflow-tooltip>
          <template #default="{ row }">{{ row.auditRemark || row.failReason || '-' }}</template>
        </el-table-column>
        <el-table-column prop="createTime" label="申请时间" width="170">
          <template #default="{ row }">{{ row.createTime || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="140">
          <template #default="{ row }">
            <template v-if="row.status === 0 || row.status === 1">
              <el-button type="success" link @click="openAudit(row, true)">通过</el-button>
              <el-button type="danger" link @click="openAudit(row, false)">拒绝</el-button>
            </template>
            <span v-else>-</span>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && list.length === 0" description="暂无提现申请" :image-size="80" />

      <el-pagination
        v-if="total > size"
        layout="total, prev, pager, next, sizes"
        :total="total"
        :page-size="size"
        :current-page="page"
        :page-sizes="[10, 20, 50, 100]"
        @current-change="load"
        @size-change="(s: number) => { size = s; load(1) }"
        style="margin-top: 12px; justify-content: flex-end"
      />
    </el-card>

    <!-- 审核弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="auditForm.approved ? '通过提现' : '拒绝提现'"
      width="440px"
      append-to-body
    >
      <el-form label-position="top">
        <el-form-item label="审核结果">
          <el-tag :type="auditForm.approved ? 'success' : 'danger'" size="small">
            {{ auditForm.approved ? '通过' : '拒绝' }}
          </el-tag>
        </el-form-item>
        <el-form-item label="审核备注">
          <el-input
            v-model="auditForm.remark"
            type="textarea"
            :rows="3"
            :placeholder="auditForm.approved ? '选填，通过备注' : '请填写拒绝原因'"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button
          :type="auditForm.approved ? 'success' : 'danger'"
          :loading="auditLoading"
          @click="submitAudit"
        >
          确认{{ auditForm.approved ? '通过' : '拒绝' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.search-row {
  display: flex;
  gap: 10px;
}
</style>
