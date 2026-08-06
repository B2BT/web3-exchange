<script setup lang="ts">
defineProps<{ title: string; desc?: string }>()
</script>

<template>
  <div class="page-container">
    <div class="ph-card g-card">
      <el-empty :description="desc || `${title}模块开发中（F2/F3 接力）`" :image-size="160">
        <el-button type="primary" @click="$router.push('/market')">返回行情</el-button>
      </el-empty>
    </div>
  </div>
</template>

<style scoped>
.ph-card {
  padding: 48px 20px;
}
</style>
