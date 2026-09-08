<script setup lang="ts">
/** 会员档案编辑 · 档案变更时间轴 */
import type { TimelineEntry } from '../types'

defineProps<{ items: TimelineEntry[] }>()

const whoColor = (w: string) => (w === 'agent' ? 'var(--dt-primary)' : w === 'system' ? 'var(--dt-gold)' : 'var(--dt-info)')
const whoLabel = (w: string) => (w === 'agent' ? '红娘' : w === 'system' ? '系统' : '会员自助')
</script>

<template>
  <GlassCard class="timeline-card" padding="">
    <div class="section-title" style="margin-bottom: 16px"><span class="section-title-bar" />档案变更时间轴</div>
    <div class="timeline">
      <div v-for="(t, i) in items" :key="i" class="tl-item">
        <span class="tl-dot" :style="{ background: whoColor(t.who) }" />
        <div class="tl-content">
          <div class="tl-head">
            <span class="tl-text">{{ t.text }}</span>
            <span class="tl-who" :style="{ color: whoColor(t.who) }">{{ whoLabel(t.who) }}</span>
          </div>
          <div v-if="t.from" class="tl-change">
            <s>{{ t.from }}</s> <app-icon name="chevron-right" :size="11" /> <b>{{ t.to }}</b>
          </div>
          <div v-if="t.detail" class="tl-detail">{{ t.detail }}</div>
          <div class="tl-time">{{ t.time }}</div>
        </div>
      </div>
    </div>
  </GlassCard>
</template>

<style scoped>
.timeline-card { padding: 20px; }
.timeline { position: relative; padding-left: 6px; }
.tl-item { position: relative; display: flex; gap: 14px; padding-bottom: 18px; }
.tl-item:not(:last-child)::before { content: ''; position: absolute; left: 5px; top: 14px; bottom: 0; width: 2px; background: var(--dt-border); }
.tl-dot { width: 12px; height: 12px; border-radius: 50%; margin-top: 3px; flex-shrink: 0; border: 2px solid #fff; box-shadow: var(--dt-shadow-sm); position: relative; z-index: 1; }
.tl-content { flex: 1; }
.tl-head { display: flex; justify-content: space-between; align-items: center; }
.tl-text { font-size: 0.875rem; font-weight: 600; color: var(--dt-ink-1); }
.tl-who { font-size: 0.6875rem; font-weight: 600; }
.tl-change { font-size: 0.75rem; color: var(--dt-ink-3); margin-top: 4px; display: flex; align-items: center; gap: 6px; }
.tl-change s { color: var(--dt-ink-4); }
.tl-change b { color: var(--dt-gold-dark); }
.tl-detail { font-size: 0.75rem; color: var(--dt-ink-3); margin-top: 2px; }
.tl-time { font-size: 0.6875rem; color: var(--dt-ink-4); margin-top: 4px; }
</style>
