<script setup lang="ts">
/** 实时动态流 — 数据源：screen.activityFeed */
import { computed, inject, ref } from 'vue'
import ScreenPanel from './ScreenPanel.vue'

/** action → 圆点颜色，未知类型回落到默认色 */
const ACTION_COLOR: Record<string, string> = {
  牵线: '#c3a05a', 匹配: '#c3a05a',
  注册: '#a8863f', 好评: '#a8863f', 评价: '#a8863f',
  报名: '#6f8fb0', 活动: '#6f8fb0',
  上线: '#5f9c78', 成功: '#5f9c78',
  成交: '#8f3a4c', 付费: '#8f3a4c',
}

const screen = inject<any>('screen', ref(null))

const feed = computed<any[]>(() =>
  ((screen.value?.activityFeed ?? []) as any[]).map((f: any) => ({
    text: f?.action ? `【${f.action}】${f.text ?? ''}` : String(f?.text ?? ''),
    time: f?.time ?? '',
    color: ACTION_COLOR[String(f?.action ?? '')] ?? '#c3a05a',
  })),
)
</script>

<template>
  <ScreenPanel title="实时动态">
    <div class="feed">
      <div v-for="(f, i) in feed" :key="i" class="feed-item">
        <span class="feed-dot" :style="{ background: f.color }" />
        <span class="feed-text">{{ f.text }}</span>
        <span class="feed-time">{{ f.time }}</span>
      </div>
    </div>
  </ScreenPanel>
</template>

<style scoped>
.feed { display: flex; flex-direction: column; gap: 8px; }
.feed-item { display: flex; align-items: center; gap: 8px; font-size: 0.75rem; }
.feed-dot { width: 7px; height: 7px; border-radius: 50%; flex-shrink: 0; }
.feed-text { flex: 1; color: var(--bs-text); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.feed-time { font-size: 0.6875rem; color: #6b7178; flex-shrink: 0; font-variant-numeric: tabular-nums; }
</style>
