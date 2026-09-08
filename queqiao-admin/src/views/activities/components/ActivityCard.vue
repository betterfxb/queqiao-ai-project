<script setup lang="ts">
/**
 * ActivityCard — 活动卡片（日期块 + 状态 + 标题/地点 + 数据 + 进度 + 负责红娘/操作）
 */
import { computed } from 'vue'
import type { Activity } from '@/api'

const props = defineProps<{ activity: Activity }>()
const emit = defineEmits<{
  (e: 'manage', a: Activity): void
  (e: 'review', a: Activity): void
  (e: 'edit', a: Activity): void
}>()

const statusClass = computed(() => {
  const s = props.activity.status
  return s === '报名中' ? 'gold' : s === '进行中' || s === '已满员' ? 'active' : s === '已结束' ? 'neutral' : 'neutral'
})
const dateTone = computed(() => {
  const s = props.activity.status
  return s === '报名中' ? 'gold' : s === '已满员' || s === '进行中' ? 'burgundy' : 'gray'
})
const pct = computed(() => Math.round((props.activity.signed / props.activity.capacity) * 100))
</script>

<template>
  <div class="glass-card act-card" :class="{ draft: activity.status === '草稿' }">
    <div class="act-card-top">
      <div class="act-date" :class="dateTone">
        <div class="act-date-m">{{ activity.date.split('.')[1] }}月</div>
        <div class="act-date-d">{{ activity.date.split('.')[2] }}</div>
      </div>
      <span class="status-pill" :class="statusClass">{{ activity.status }}</span>
    </div>
    <div class="act-title">{{ activity.title }}</div>
    <div class="act-place"><app-icon name="map-pin" :size="12" />{{ activity.place }}</div>
    <div class="act-stats">
      <span>报名 <b>{{ activity.signed }}/{{ activity.capacity }}</b></span>
      <span>预期 <b>{{ activity.expectedPairs }}对</b></span>
      <span>收入 <b>¥{{ activity.revenue.toLocaleString() }}</b></span>
    </div>
    <div class="act-progress">
      <div class="act-progress-track">
        <div
          class="act-progress-fill"
          :class="{ low: pct < 40, done: activity.status === '已结束' }"
          :style="{ width: pct + '%' }"
        />
      </div>
    </div>
    <div class="act-foot">
      <div class="act-agent"><span class="act-agent-avatar">{{ activity.agent[0] }}</span>{{ activity.agent }}</div>
      <button v-if="activity.status === '草稿'" class="btn btn-sm btn-ghost" style="border-style:dashed" @click="emit('edit', activity)">继续编辑</button>
      <button v-else-if="activity.status === '已结束'" class="btn btn-sm btn-outline" @click="emit('review', activity)">复盘</button>
      <button v-else class="btn btn-sm btn-outline" @click="emit('manage', activity)">管理</button>
    </div>
  </div>
</template>

<style scoped>
.act-card { padding: 16px; transition: all 0.15s; }
.act-card:hover { transform: translateY(-2px); box-shadow: var(--dt-shadow-lg); }
.act-card.draft { opacity: 0.7; }
.act-card-top { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 12px; }
.act-date { width: 52px; border-radius: 10px; text-align: center; padding: 6px 0; color: #fff; }
.act-date.gold { background: var(--dt-gold-gradient); }
.act-date.burgundy { background: var(--dt-primary); }
.act-date.gray { background: var(--dt-ink-4); }
.act-date-m { font-size: 0.6875rem; opacity: 0.9; }
.act-date-d { font-size: 1.25rem; font-weight: 800; line-height: 1; }
.act-title { font-size: 0.875rem; font-weight: 700; color: var(--dt-ink-1); margin-bottom: 6px; }
.act-place { font-size: 0.75rem; color: var(--dt-ink-3); display: flex; align-items: center; gap: 3px; margin-bottom: 10px; }
.act-stats { display: flex; gap: 12px; font-size: 0.75rem; color: var(--dt-ink-3); margin-bottom: 10px; }
.act-stats b { color: var(--dt-ink-1); }
.act-progress { margin-bottom: 12px; }
.act-progress-track { height: 6px; border-radius: 999px; background: var(--dt-muted); overflow: hidden; }
.act-progress-fill { height: 100%; border-radius: 999px; background: var(--dt-gold-gradient); }
.act-progress-fill.low { background: var(--dt-warning); }
.act-progress-fill.done { background: var(--dt-ink-4); }
.act-foot { display: flex; align-items: center; justify-content: space-between; }
.act-agent { display: flex; align-items: center; gap: 6px; font-size: 0.75rem; color: var(--dt-ink-2); }
.act-agent-avatar { width: 22px; height: 22px; border-radius: 50%; background: var(--dt-gold-gradient); color: #fff; font-size: 0.6875rem; font-weight: 700; display: inline-flex; align-items: center; justify-content: center; }
</style>
