<script setup lang="ts">
/**
 * CalendarGrid — 周视图时间轴日历（日期表头 + 时间列 + 事件块 + 当前时间线）
 * 事件来自后端 /api/calendar/events，按 weekStart 渲染当周 7 天。
 */
import { computed } from 'vue'
import type { CalEvent } from '@/api'

const props = defineProps<{ events: CalEvent[]; weekStart: Date }>()
const emit = defineEmits<{ (e: 'open', ev: CalEvent): void; (e: 'add'): void }>()

const hours = ['08:00','09:00','10:00','11:00','12:00','13:00','14:00','15:00','16:00','17:00','18:00','19:00','20:00','21:00']
const WEEK = ['一','二','三','四','五','六','日']

const days = computed(() => {
  const ws = props.weekStart
  const today = new Date()
  return Array.from({ length: 7 }, (_, i) => {
    const d = new Date(ws)
    d.setDate(ws.getDate() + i)
    return { label: WEEK[i], date: d.getDate(), isToday: d.toDateString() === today.toDateString() }
  })
})

const monthBadge = computed(() => props.weekStart.getMonth() + 1 + '月')

const typeClass = (t?: string) => {
  if (!t) return 'meeting'
  const s = t.toLowerCase()
  if (t.includes('约见') || s.includes('date')) return 'date'
  if (t.includes('活动') || s.includes('activity')) return 'activity'
  if (t.includes('会议') || s.includes('meeting')) return 'meeting'
  if (t.includes('审核') || t.includes('回访') || s.includes('review')) return 'review'
  return 'meeting'
}

const parseH = (s?: string) => {
  if (!s || !s.includes(':')) return 9
  const [h, m] = s.split(':').map(Number)
  return h + (m || 0) / 60
}

const weekEvents = computed(() => {
  const ws = props.weekStart
  const we = new Date(ws)
  we.setDate(ws.getDate() + 7)
  return (props.events || []).filter((e) => {
    if (!e.date) return false
    const d = new Date(e.date + 'T00:00:00')
    return d >= ws && d < we
  })
})

const dayIndex = (date?: string) => {
  if (!date) return -1
  const d = new Date(date + 'T00:00:00')
  const diff = Math.round((d.getTime() - props.weekStart.getTime()) / 86400000)
  return diff >= 0 && diff < 7 ? diff : -1
}

const nowTop = computed(() => {
  const now = new Date()
  const di = dayIndex(now.toISOString().slice(0, 10))
  if (di < 0) return null
  return (now.getHours() - 8) * 48 + (now.getMinutes() / 60) * 48
})
</script>

<template>
  <div class="glass-card week-card">
    <div class="week-head">
      <div class="month-badge">{{ monthBadge }}</div>
      <div v-for="d in days" :key="d.date" class="day-head" :class="{ today: d.isToday }">
        <div class="day-label">{{ d.label }}</div>
        <div class="day-date">{{ d.date }}</div>
        <div v-if="d.isToday" class="today-badge">今天</div>
      </div>
    </div>
    <div class="week-grid">
      <div class="time-col">
        <div v-for="h in hours" :key="h" class="time-cell">{{ h }}</div>
      </div>
      <div class="grid-cols">
        <div v-for="(d, di) in days" :key="d.date" class="grid-col" :class="{ today: d.isToday }" @click="emit('add')">
          <div v-for="h in hours" :key="h" class="grid-cell" />
          <div
            v-for="(e, ei) in weekEvents.filter((x) => dayIndex(x.date) === di)"
            :key="ei"
            class="event"
            :class="typeClass(e.type)"
            :style="{ top: (parseH(e.startTime) - 8) * 48 + 'px', height: Math.max(1, (parseH(e.endTime || e.startTime) - parseH(e.startTime)) * 48 - 4) + 'px' }"
            @click.stop="emit('open', e)"
          >
            <div class="event-title">{{ e.title }}</div>
            <div v-if="e.place" class="event-place">{{ e.startTime }} · {{ e.place }}</div>
          </div>
        </div>
        <div v-if="nowTop !== null" class="now-line" :style="{ top: nowTop + 'px' }"><span class="now-dot" /></div>
      </div>
    </div>
    <div class="legend">
      <span class="legend-item"><span class="lg date" />约见</span>
      <span class="legend-item"><span class="lg activity" />活动</span>
      <span class="legend-item"><span class="lg meeting" />会议</span>
      <span class="legend-item"><span class="lg review" />审核·回访</span>
    </div>
  </div>
</template>

<style scoped>
.week-card { flex: 1; min-width: 0; padding: 18px; }
.week-head { display: flex; align-items: center; margin-bottom: 8px; }
.month-badge { font-size: 1rem; font-weight: 600; color: var(--dt-primary); width: 60px; flex-shrink: 0; }
.day-head { flex: 1; text-align: center; padding: 8px 0; border-radius: var(--dt-radius-md); position: relative; }
.day-head.today { background: var(--dt-primary-soft); }
.day-label { font-size: 0.75rem; color: var(--dt-ink-3); }
.day-date { font-size: 1.0625rem; font-weight: 600; color: var(--dt-ink-1); }
.day-head.today .day-date { color: var(--dt-primary); }
.today-badge { font-size: 0.625rem; color: var(--dt-gold-dark); background: var(--dt-gold-soft); padding: 1px 8px; border-radius: var(--dt-radius-sm); display: inline-block; margin-top: 2px; }

.week-grid { display: flex; position: relative; }
.time-col { width: 60px; flex-shrink: 0; }
.time-cell { height: 48px; font-size: 0.6875rem; color: var(--dt-ink-4); text-align: right; padding-right: 8px; box-sizing: border-box; }
.grid-cols { flex: 1; display: flex; position: relative; }
.grid-col { flex: 1; position: relative; border-left: 1px solid var(--dt-border); cursor: pointer; }
.grid-col.today { background: var(--dt-primary-soft); }
.grid-cell { height: 48px; border-bottom: 1px solid var(--dt-border); box-sizing: border-box; }
.event { position: absolute; left: 3px; right: 3px; border-radius: var(--dt-radius-md); padding: 4px 6px; font-size: 0.6875rem; overflow: hidden; box-sizing: border-box; color: #fff; cursor: pointer; }
.event.date { background: var(--dt-primary); }
.event.activity { background: var(--dt-gold); }
.event.meeting { background: var(--dt-ink-3); }
.event.review { background: var(--dt-info); }
.event-title { font-weight: 600; line-height: 1.2; }
.event-place { font-size: 0.625rem; opacity: 0.9; margin-top: 2px; }
.now-line { position: absolute; left: 0; right: 0; height: 2px; background: var(--dt-error); }
.now-dot { position: absolute; left: -4px; top: -3px; width: 8px; height: 8px; border-radius: 50%; background: var(--dt-error); }

.legend { display: flex; align-items: center; gap: 16px; margin-top: 14px; font-size: 0.75rem; color: var(--dt-ink-3); }
.legend-item { display: inline-flex; align-items: center; gap: 6px; }
.lg { width: 12px; height: 12px; border-radius: var(--dt-radius-sm); display: inline-block; }
.lg.date { background: var(--dt-primary); }
.lg.activity { background: var(--dt-gold); }
.lg.meeting { background: var(--dt-ink-3); }
.lg.review { background: var(--dt-info); }
</style>
