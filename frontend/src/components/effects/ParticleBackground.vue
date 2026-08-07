<script setup lang="ts">
/**
 * 全屏 Canvas 粒子背景（零依赖）：漂浮渐变粒子 + 连线。
 * 固定在最底层，pointer-events:none，不遮挡交互。
 */
import { onBeforeUnmount, onMounted, ref } from 'vue'

const canvasRef = ref<HTMLCanvasElement>()
let ctx: CanvasRenderingContext2D | null = null
let raf = 0
let W = 0
let H = 0
let pts: { x: number; y: number; vx: number; vy: number; r: number; hue: number }[] = []
const N = 60

function resize() {
  if (!canvasRef.value) return
  W = canvasRef.value.width = window.innerWidth
  H = canvasRef.value.height = window.innerHeight
}

function init() {
  pts = Array.from({ length: N }, () => ({
    x: Math.random() * W,
    y: Math.random() * H,
    vx: (Math.random() - 0.5) * 0.4,
    vy: (Math.random() - 0.5) * 0.4,
    r: Math.random() * 2 + 0.6,
    hue: 250 + Math.random() * 60, // 紫~青
  }))
}

function tick() {
  if (!ctx) return
  ctx.clearRect(0, 0, W, H)
  for (const p of pts) {
    p.x += p.vx
    p.y += p.vy
    if (p.x < 0 || p.x > W) p.vx *= -1
    if (p.y < 0 || p.y > H) p.vy *= -1
    ctx.beginPath()
    ctx.arc(p.x, p.y, p.r, 0, Math.PI * 2)
    ctx.fillStyle = `hsla(${p.hue}, 90%, 70%, 0.7)`
    ctx.shadowBlur = 8
    ctx.shadowColor = `hsla(${p.hue}, 90%, 60%, 0.8)`
    ctx.fill()
  }
  // 连线
  for (let i = 0; i < pts.length; i++) {
    for (let j = i + 1; j < pts.length; j++) {
      const dx = pts[i].x - pts[j].x
      const dy = pts[i].y - pts[j].y
      const d = Math.hypot(dx, dy)
      if (d < 130) {
        ctx.beginPath()
        ctx.moveTo(pts[i].x, pts[i].y)
        ctx.lineTo(pts[j].x, pts[j].y)
        ctx.strokeStyle = `hsla(265, 80%, 65%, ${0.18 * (1 - d / 130)})`
        ctx.lineWidth = 1
        ctx.stroke()
      }
    }
  }
  raf = requestAnimationFrame(tick)
}

onMounted(() => {
  if (!canvasRef.value) return
  ctx = canvasRef.value.getContext('2d')
  resize()
  init()
  tick()
  window.addEventListener('resize', resize)
})
onBeforeUnmount(() => {
  cancelAnimationFrame(raf)
  window.removeEventListener('resize', resize)
})
</script>

<template>
  <canvas ref="canvasRef" class="particle-canvas" aria-hidden="true"></canvas>
</template>

<style scoped>
.particle-canvas {
  position: fixed;
  inset: 0;
  z-index: 0;
  pointer-events: none;
  opacity: 0.5;
}
</style>
