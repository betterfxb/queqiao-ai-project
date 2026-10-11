<script setup lang="ts">
/** 大屏面板通用外框：暗色渐变底 + 金色描角 + 菱形标题 */
defineProps<{ title: string }>()
</script>

<template>
  <div class="bs-panel">
    <div class="bs-panel-title"><span class="diamond" />{{ title }}</div>
    <slot />
  </div>
</template>

<style scoped>
.bs-panel { position: relative; background: #191c21; border: 1px solid var(--bs-border); border-radius: var(--dt-radius-lg); padding: 14px; flex-shrink: 0; }
.bs-panel-title { display: flex; align-items: center; gap: 8px; font-size: 0.9375rem; font-weight: 600; color: var(--bs-text); margin-bottom: 12px; }
.diamond { width: 3px; height: 14px; border-radius: 2px; background: var(--bs-burgundy); flex-shrink: 0; }
</style>
