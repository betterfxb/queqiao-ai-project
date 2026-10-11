<script setup lang="ts">
/** 热门匹配标签云 — 数据源：screen.hotTags */
import { computed, inject, ref } from 'vue'
import ScreenPanel from './ScreenPanel.vue'

const screen = inject<any>('screen', ref(null))

/** 按热度倒序，前两位放大展示 */
const hotTags = computed<any[]>(() =>
  ((screen.value?.hotTags ?? []) as any[])
    .slice()
    .sort((a: any, b: any) => Number(b?.value || 0) - Number(a?.value || 0)),
)
</script>

<template>
  <ScreenPanel title="热门匹配标签">
    <div class="tag-cloud">
      <span v-for="(t, i) in hotTags" :key="t.name" class="hot-tag" :class="{ xl: i < 2, gold: i % 3 === 0, burgundy: i % 3 === 1 }" :title="`${t.name} · ${t.value}`">{{ t.name }}</span>
    </div>
  </ScreenPanel>
</template>

<style scoped>
.tag-cloud { display: flex; flex-wrap: wrap; gap: 8px; }
.hot-tag { font-size: 0.75rem; padding: 4px 12px; border-radius: var(--dt-radius-pill); background: var(--bs-panel); color: #9aa1a9; }
.hot-tag.xl { font-size: 0.9375rem; font-weight: 600; color: var(--bs-gold); }
.hot-tag.gold { border: 1px solid rgba(155,130,83,0.4); }
.hot-tag.burgundy { background: rgba(143,58,76,0.22); color: #cbb0b6; }
</style>
