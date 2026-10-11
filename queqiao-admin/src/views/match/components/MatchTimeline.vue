<script setup lang="ts">
/** 牵线详情 · 牵线进展时间线 + 底部操作 */
import type { ProgressItem } from '../types'

defineProps<{ items: ProgressItem[] }>()
const emit = defineEmits<{
  (e: 'remind'): void
  (e: 'schedule'): void
  (e: 'feedback'): void
  (e: 'fail'): void
}>()
</script>

<template>
  <GlassCard class="timeline-card" padding="">
    <div class="section-title"><span class="section-title-bar" />牵线进展</div>
    <div class="ptl">
      <div v-for="(t, i) in items" :key="i" class="ptl-item">
        <span class="ptl-dot" :style="{ background: t.color }" />
        <div class="ptl-text">{{ t.text }}</div>
        <div class="ptl-time">{{ t.time }}</div>
      </div>
    </div>
    <div class="ptl-actions">
      <button class="btn btn-ghost" @click="emit('remind')">发送提醒</button>
      <button class="btn btn-primary" @click="emit('schedule')"><app-icon name="calendar" :size="15" />安排约见</button>
      <button class="btn btn-outline" @click="emit('feedback')"><app-icon name="comment" :size="15" />记录反馈</button>
      <button class="btn btn-danger" @click="emit('fail')">标记失败</button>
    </div>
  </GlassCard>
</template>

<style scoped>
.timeline-card { padding: 24px; }
.ptl { position: relative; padding-left: 6px; margin: 18px 0; }
.ptl-item { position: relative; display: flex; align-items: baseline; gap: 12px; padding-bottom: 16px; }
.ptl-item:not(:last-child)::before { content: ''; position: absolute; left: 5px; top: 12px; bottom: 0; width: 2px; background: var(--dt-border); }
.ptl-dot { width: 12px; height: 12px; border-radius: 50%; flex-shrink: 0; border: 2px solid var(--dt-card-solid); position: relative; z-index: 1; margin-top: 2px; }
.ptl-text { flex: 1; font-size: 0.8125rem; color: var(--dt-ink-1); }
.ptl-time { font-size: 0.75rem; color: var(--dt-ink-4); white-space: nowrap; }
.ptl-actions { display: flex; gap: 12px; }
</style>
