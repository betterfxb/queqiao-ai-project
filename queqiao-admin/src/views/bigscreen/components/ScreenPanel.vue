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
.bs-panel { position: relative; background: linear-gradient(160deg, rgba(154,51,73,0.16), rgba(154,51,73,0.08)); border: 1px solid rgba(232,200,120,0.22); border-radius: 10px; padding: 14px; flex-shrink: 0; }
.bs-panel::before { content: ''; position: absolute; top: -1px; right: -1px; width: 16px; height: 16px; border-top: 2px solid #e8c878; border-right: 2px solid #e8c878; }
.bs-panel::after { content: ''; position: absolute; bottom: -1px; left: -1px; width: 16px; height: 16px; border-bottom: 2px solid #e8c878; border-left: 2px solid #e8c878; }
.bs-panel-title { display: flex; align-items: center; gap: 8px; font-size: 0.875rem; font-weight: 700; color: #e8c878; margin-bottom: 12px; }
.diamond { width: 8px; height: 8px; background: #e8c878; transform: rotate(45deg); flex-shrink: 0; }
</style>
