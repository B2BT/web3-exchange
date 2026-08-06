<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { placeOrder } from '@/api/order'
import type { PlaceOrderResult } from '@/api/order'
import { useAuthStore } from '@/stores/auth'
import { DEFAULT_SYMBOLS, coinDecimals, symbolParts } from '@/config/market'
import { formatLong, toLong } from '@/utils/format'

const authStore = useAuthStore()

const symbols = DEFAULT_SYMBOLS
const currentSymbol = ref('BTC/USDT')
/** 方向：1=买入 2=卖出 */
const side = ref<number>(1)
/** 类型：1=限价 2=市价 */
const orderType = ref<number>(1)
const price = ref('')
const quantity = ref('')
const quoteAmount = ref('')
const submitting = ref(false)

const parts = computed(() => symbolParts(currentSymbol.value))
const quoteDecimals = computed(() => coinDecimals(parts.value.quote))
const baseDecimals = computed(() => coinDecimals(parts.value.base))
const isBuy = computed(() => side.value === 1)

function resetFields() {
  price.value = ''
  quantity.value = ''
  quoteAmount.value = ''
}

function switchSide(name: string | number) {
  side.value = Number(name)
  resetFields()
}
function switchType(t: number) {
  orderType.value = t
  resetFields()
}

/** 限价模式是否显示价格/数量输入 */
const showLimit = computed(() => orderType.value === 1)
/** 市价买：输入 quoteAmount；市价卖：输入 quantity */
const showQuoteAmount = computed(() => orderType.value === 2 && isBuy.value)
const showQuantity = computed(() => orderType.value === 1 || !isBuy.value)

const buttonType = computed(() => (isBuy.value ? 'success' : 'danger'))
const buttonText = computed(() =>
  isBuy.value ? (orderType.value === 1 ? '买入' : '市价买入') : orderType.value === 1 ? '卖出' : '市价卖出',
)

async function submit() {
  const userId = authStore.userInfo?.id
  if (!userId) {
    ElMessage.warning('请先登录')
    return
  }
  // 校验
  if (orderType.value === 1) {
    if (!price.value || Number(price.value) <= 0) return void ElMessage.warning('请输入有效的限价')
    if (!quantity.value || Number(quantity.value) <= 0) return void ElMessage.warning('请输入有效的数量')
  } else if (isBuy.value) {
    if (!quoteAmount.value || Number(quoteAmount.value) <= 0)
      return void ElMessage.warning('请输入有效的买入金额')
  } else if (!quantity.value || Number(quantity.value) <= 0) {
    return void ElMessage.warning('请输入有效的卖出数量')
  }

  const payload = {
    userId,
    symbol: currentSymbol.value,
    side: side.value,
    orderType: orderType.value,
    // 限价单：传 price
    price: showLimit.value ? toLong(Number(price.value), quoteDecimals.value) : 0,
    // 市价卖 或 限价：传 quantity；市价买不传数量
    quantity: showQuantity.value ? toLong(Number(quantity.value), baseDecimals.value) : 0,
    // 市价买：传 quoteAmount
    quoteAmount: showQuoteAmount.value
      ? toLong(Number(quoteAmount.value), quoteDecimals.value)
      : 0,
  }

  submitting.value = true
  try {
    const res: PlaceOrderResult = await placeOrder(payload)
    const status = res.order?.status
    if (status === 4) {
      // REJECTED：余额不足/风控等原因
      ElMessage.warning(
        `下单被拒绝：${res.order?.remark || '资金不足或风控拦截（请先充值资产）'}`,
      )
    } else if (status === 0) {
      ElMessage.success(`下单成功（挂单中） 单号 ${res.order?.orderNo || ''}`)
    } else if (status === 2) {
      ElMessage.success(`下单成功（已成交） 单号 ${res.order?.orderNo || ''}`)
    } else if (status === 1) {
      ElMessage.success(`下单成功（部分成交） 单号 ${res.order?.orderNo || ''}`)
    } else {
      ElMessage.success(`下单成功 单号 ${res.order?.orderNo || ''}`)
    }
    resetFields()
  } catch (e: any) {
    // 后端错误信息已由 http 拦截器统一弹出
    // eslint-disable-next-line no-empty
    void e
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="trade-page">
    <el-card shadow="never" class="trade-card">
      <template #header>
        <div class="trade-title">
          <span>现货交易</span>
          <el-select
            v-model="currentSymbol"
            style="width: 160px"
            placeholder="选择交易对"
            @change="resetFields"
          >
            <el-option v-for="s in symbols" :key="s.symbol" :label="s.symbol" :value="s.symbol" />
          </el-select>
        </div>
      </template>

      <!-- 买/卖 tab（:model-value 用 String，tab-change 再转回 number，避免 string 污染） -->
      <el-tabs
        :model-value="String(side)"
        class="side-tabs"
        @tab-change="switchSide"
      >
        <el-tab-pane label="买入" name="1" />
        <el-tab-pane label="卖出" name="2" />
      </el-tabs>

      <!-- 限价/市价切换 -->
      <el-segmented
        :model-value="String(orderType)"
        :options="[
          { label: '限价', value: '1' },
          { label: '市价', value: '2' },
        ]"
        @update:model-value="switchType(Number($event))"
        class="type-seg"
      />

      <el-form label-position="top" class="order-form">
        <!-- 限价输入 -->
        <el-form-item v-if="showLimit" :label="`限价 (${parts.quote})`">
          <el-input v-model="price" placeholder="请输入限价" />
        </el-form-item>

        <!-- 数量输入：限价单 & 市价卖单 -->
        <el-form-item v-if="showQuantity" :label="`数量 (${parts.base})`">
          <el-input v-model="quantity" placeholder="请输入数量" />
        </el-form-item>

        <!-- 市价买单：输入金额 -->
        <el-form-item v-if="showQuoteAmount" :label="`买入金额 (${parts.quote})`">
          <el-input v-model="quoteAmount" placeholder="请输入预算金额" />
        </el-form-item>
      </el-form>

      <el-button
        :type="buttonType"
        size="large"
        style="width: 100%"
        :loading="submitting"
        @click="submit"
      >
        {{ buttonText }}
      </el-button>

      <el-alert
        type="info"
        :closable="false"
        show-icon
        class="balance-tip"
        title="下单前请确保账户有足够资产余额"
        description="余额不足或风控拦截时，后端会返回拒绝原因（请先到「资产」模块充值）。金额输入为人类可读数值，提交时自动换算为最小单位。"
      />
    </el-card>
  </div>
</template>

<style scoped>
.trade-page {
  padding: 16px;
  display: flex;
  justify-content: center;
}
.trade-card {
  width: 480px;
  max-width: 100%;
}
.trade-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
}
.type-seg {
  margin: 12px 0 16px;
}
.side-tabs :deep(.el-tabs__item) {
  font-size: 16px;
  font-weight: 600;
}
.balance-tip {
  margin-top: 16px;
}
</style>
