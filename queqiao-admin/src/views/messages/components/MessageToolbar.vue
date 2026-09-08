<script setup lang="ts">
/**
 * 消息中心 · 会话栏顶部工具条
 * 搜索框 + 分类筛选标签；受控组件，值由 index.vue 持有。
 */
defineProps<{
  tabs: string[]
  search: string
  tab: string
}>()

const emit = defineEmits<{
  (e: 'update:search', v: string): void
  (e: 'update:tab', v: string): void
}>()

const onInput = (e: Event) => emit('update:search', (e.target as HTMLInputElement).value)
</script>

<template>
  <div class="conv-search">
    <app-icon name="search" :size="15" />
    <input :value="search" placeholder="搜索对话..." @input="onInput" />
  </div>
  <div class="conv-tabs">
    <span
      v-for="t in tabs"
      :key="t"
      class="conv-tab"
      :class="{ active: tab === t }"
      @click="emit('update:tab', t)"
    >{{ t }}</span>
  </div>
</template>

<style scoped>
.conv-search { display: flex; align-items: center; gap: 8px; margin: 14px; padding: 0 12px; height: 36px; border-radius: 999px; background: var(--dt-glass-light); border: 1px solid var(--dt-border); color: var(--dt-ink-3); }
.conv-search input { flex: 1; border: none; outline: none; background: transparent; font-size: 0.8125rem; font-family: var(--dt-font); }
.conv-tabs { display: flex; gap: 4px; padding: 0 14px 12px; }
.conv-tab { font-size: 0.75rem; padding: 4px 12px; border-radius: 999px; color: var(--dt-ink-3); cursor: pointer; background: var(--dt-muted); }
.conv-tab.active { background: var(--dt-primary); color: #fff; font-weight: 600; }
</style>
