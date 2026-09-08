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
.hot-tag { font-size: 0.75rem; padding: 4px 12px; border-radius: 999px; background: rgba(232,200,120,0.08); color: rgba(240,230,210,0.8); }
.hot-tag.xl { font-size: 0.9375rem; font-weight: 700; color: #e8c878; }
.hot-tag.gold { border: 1px solid rgba(232,200,120,0.3); }
.hot-tag.burgundy { background: rgba(154,51,73,0.25); color: #d9a3b0; }
</style>
