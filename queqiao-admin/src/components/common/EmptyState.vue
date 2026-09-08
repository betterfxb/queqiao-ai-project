<script setup lang="ts">
/** EmptyState — 通用空状态占位 */
withDefaults(defineProps<{ text?: string; icon?: string }>(), {
  text: '暂无数据',
  icon: 'inbox',
})
</script>

<template>
  <div class="empty-state">
    <app-icon :name="icon" :size="32" class="empty-icon" />
    <div class="empty-text">{{ text }}</div>
  </div>
</template>

<style scoped>
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 40px 0;
  color: var(--dt-ink-4);
}
.empty-icon { opacity: 0.5; }
.empty-text { font-size: 0.8125rem; }
</style>
