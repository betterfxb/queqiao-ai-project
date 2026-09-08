<script setup lang="ts">
/** 实时动态流 — 数据源：screen.activityFeed */
import { computed, inject, ref } from 'vue'
import ScreenPanel from './ScreenPanel.vue'

/** action → 圆点颜色，未知类型回落到默认金色 */
const ACTION_COLOR: Record<string, string> = {
  牵线: '#e8c878', 匹配: '#e8c878',
  注册: '#c9a96e', 好评: '#c9a96e', 评价: '#c9a96e',
  报名: '#7a9ab8', 活动: '#7a9ab8',
  上线: '#4a8b5c', 成功: '#4a8b5c',
  成交: '#9a3349', 付费: '#9a3349',
}

const screen = inject<any>('screen', ref(null))

const feed = computed<any[]>(() =>
  ((screen.value?.activityFeed ?? []) as any[]).map((f: any) => ({
    text: f?.action ? `【${f.action}】${f.text ?? ''}` : String(f?.text ?? ''),
    time: f?.time ?? '',
    color: ACTION_COLOR[String(f?.action ?? '')] ?? '#c9a96e',
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
.feed-text { flex: 1; color: rgba(240,230,210,0.85); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.feed-time { font-size: 0.6875rem; color: rgba(240,230,210,0.45); flex-shrink: 0; }
</style>
