<script setup lang="ts">
/**
 * CalendarSidePanel — 右侧栏：今日日程 + 即将到来
 * 数据来自后端 /api/calendar/today 与同月事件，无本地假数据。
 */
import type { CalEvent } from '@/api'

const props = defineProps<{ today: CalEvent[]; upcoming: CalEvent[] }>()
const emit = defineEmits<{ (e: 'add'): void; (e: 'open', ev: CalEvent): void }>()

const fmt = (d?: string) => (d ? d.slice(5).replace('-', '/') : '')
</script>

<template>
  <aside class="cal-side">
    <div class="glass-card side-card">
      <div class="side-head">今日日程 <span class="today-pill">今天</span></div>
      <div class="side-sub">共 {{ today.length }} 项</div>
      <div v-for="(t, i) in today" :key="i" class="today-item" :class="{ highlight: t.status !== '已完成' }" @click="emit('open', t)">
        <div class="today-time">{{ t.startTime || '--:--' }}</div>
        <div class="today-info">
          <div class="today-title">{{ t.title }}</div>
          <div class="today-place">{{ t.place || t.type || '' }}</div>
        </div>
        <app-icon :name="t.status === '已完成' ? 'check-circle' : 'clock'" :size="16" :style="{ color: t.status === '已完成' ? 'var(--dt-success)' : 'var(--dt-warning)' }" />
      </div>
      <div v-if="!today.length" class="side-empty">今日暂无日程</div>
    </div>

    <div class="glass-card side-card">
      <div class="side-head">即将到来</div>
      <div v-for="(u, i) in upcoming" :key="i" class="up-item" @click="emit('open', u)">
        <div class="up-date">{{ fmt(u.date) }}</div>
        <div>
          <div class="up-title">{{ u.title }}</div>
          <div class="up-sub">{{ u.place || u.type || '待定' }}</div>
        </div>
      </div>
      <div v-if="!upcoming.length" class="side-empty">近期暂无安排</div>
      <button class="add-remind" @click="emit('add')"><app-icon name="plus" :size="14" />新建日程</button>
    </div>
  </aside>
</template>

<style scoped>
.cal-side { width: 320px; flex-shrink: 0; display: flex; flex-direction: column; gap: 20px; }
.side-card { padding: 18px; }
.side-head { font-size: 0.9375rem; font-weight: 800; color: var(--dt-ink-1); display: flex; align-items: center; gap: 8px; }
.today-pill { font-size: 0.625rem; background: var(--dt-gold-soft); color: var(--dt-gold-dark); padding: 1px 8px; border-radius: 999px; }
.side-sub { font-size: 0.75rem; color: var(--dt-ink-3); margin: 4px 0 12px; }
.today-item { display: flex; align-items: center; gap: 10px; padding: 10px; border-radius: 10px; margin-bottom: 6px; cursor: pointer; }
.today-item.highlight { background: var(--dt-gold-soft); border: 1px solid var(--dt-border-gold); }
.today-time { font-size: 0.8125rem; font-weight: 700; color: var(--dt-primary); width: 46px; flex-shrink: 0; }
.today-info { flex: 1; min-width: 0; }
.today-title { font-size: 0.8125rem; font-weight: 600; color: var(--dt-ink-1); }
.today-place { font-size: 0.6875rem; color: var(--dt-ink-4); }
.up-item { display: flex; gap: 12px; padding: 10px 0; border-bottom: 1px solid var(--dt-border); cursor: pointer; }
.up-item:last-of-type { border-bottom: none; }
.up-date { font-size: 0.8125rem; font-weight: 800; color: var(--dt-primary); flex-shrink: 0; }
.up-title { font-size: 0.8125rem; font-weight: 600; color: var(--dt-ink-1); }
.up-sub { font-size: 0.6875rem; color: var(--dt-ink-4); }
.add-remind { width: 100%; margin-top: 12px; height: 38px; border-radius: 10px; border: 1px dashed var(--dt-border-strong); background: transparent; color: var(--dt-ink-3); font-size: 0.8125rem; cursor: pointer; display: flex; align-items: center; justify-content: center; gap: 6px; }
.add-remind:hover { color: var(--dt-primary); border-color: var(--dt-primary-light); }
.side-empty { font-size: 0.75rem; color: var(--dt-ink-4); padding: 8px 0; }
</style>
