<script setup lang="ts">
/**
 * 数字滚动（CountUp）：从 from 到 to 的缓动递增。
 * 监听 value 变化自动重放。
 */
import { onMounted, ref, watch } from 'vue'

const props = withDefaults(
  defineProps<{
    to: number
    decimals?: number
    duration?: number
  }>(),
  { decimals: 0, duration: 900 }
)

const display = ref(0)
let raf = 0

function animate() {
  cancelAnimationFrame(raf)
  const start = performance.now()
  const from = 0
  const to = props.to
  const dur = props.duration
  const step = (now: number) => {
    const p = Math.min((now - start) / dur, 1)
    const eased = 1 - Math.pow(1 - p, 3)
    display.value = from + (to - from) * eased
    if (p < 1) raf = requestAnimationFrame(step)
    else display.value = to
  }
  raf = requestAnimationFrame(step)
}

onMounted(animate)
watch(() => props.to, animate)
</script>

<template>
  <span class="count-up">{{ display.toFixed(decimals) }}</span>
</template>
