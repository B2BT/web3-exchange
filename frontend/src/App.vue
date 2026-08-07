<script setup lang="ts">
import { useAuthStore } from '@/stores/auth'
import ParticleBackground from '@/components/effects/ParticleBackground.vue'
</script>

<template>
  <div class="app-root">
    <!-- 装饰层：粒子背景 -->
    <ParticleBackground />
    <!-- 装饰层：网格线 -->
    <div class="app-bg app-bg-grid" aria-hidden="true"></div>
    <!-- 装饰层：顶部霓虹光晕 -->
    <div class="app-bg app-bg-glow" aria-hidden="true"></div>

    <div class="app-content">
      <router-view v-slot="{ Component }">
        <transition name="page-fade" mode="out-in">
          <component :is="Component" />
        </transition>
      </router-view>
    </div>
  </div>
</template>

<style scoped>
.app-root {
  position: relative;
  min-height: 100vh;
  background: var(--bg-base);
}

/* 装饰层：fixed 铺满，不挡点击，位于内容之下 */
.app-bg {
  position: fixed;
  inset: 0;
  z-index: 0;
  pointer-events: none;
}

.app-bg-grid {
  background-image: linear-gradient(rgba(255, 255, 255, 0.03) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.03) 1px, transparent 1px);
  background-size: 25px 25px;
}

.app-bg-glow {
  background: radial-gradient(600px 300px at 20% -10%, rgba(124, 58, 237, 0.18), transparent),
    radial-gradient(700px 350px at 85% -5%, rgba(34, 211, 238, 0.12), transparent);
}

/* 内容层置于装饰层之上 */
.app-content {
  position: relative;
  z-index: 1;
}

/* 页面切换：fade + 轻微上移 */
.page-fade-enter-active,
.page-fade-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}
.page-fade-enter-from {
  opacity: 0;
  transform: translateY(8px);
}
.page-fade-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}
</style>
