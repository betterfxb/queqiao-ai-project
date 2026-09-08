<script setup lang="ts">
/** Dashboard · 右侧 AI 智能助手侧栏（AI 头部 + 今日待办 + 最优匹配推荐） */
import type { MatchRec, TodoItem } from '../types'

defineProps<{ todos: TodoItem[]; recs: MatchRec[] }>()
const emit = defineEmits<{ (e: 'match', forMemberId: string): void }>()
</script>

<template>
  <aside class="right-sidebar">
    <GlassCard class="ai-header" padding="">
      <div class="ai-title-row">
        <div class="ai-sparkle"><app-icon name="star" :size="14" /></div>
        <span class="ai-title">AI智能助手</span>
      </div>
      <div class="ai-subtitle">今日待办：{{ todos.length }}项</div>
    </GlassCard>

    <GlassCard class="side-card" padding="">
      <div class="side-label"><app-icon name="check-circle" :size="12" />今日待办</div>
      <div v-for="(t, i) in todos" :key="i" class="todo-item">
        <div class="todo-check"><app-icon name="check" :size="10" /></div>
        <span class="todo-text">{{ t.text }}</span>
        <span class="todo-priority" :class="t.priority" />
      </div>
    </GlassCard>

    <GlassCard class="side-card" padding="">
      <div class="side-label"><app-icon name="star" :size="12" />最优匹配推荐</div>
      <div v-for="(r, i) in recs" :key="i" class="rec-item">
        <div class="rec-top">
          <div class="rec-avatar" :style="{ background: r.avatarBg }">{{ r.initial }}</div>
          <div class="rec-info">
            <div class="rec-name">{{ r.name }}<span class="rec-score">{{ r.score }}%</span></div>
            <div class="rec-detail">{{ r.detail }}</div>
          </div>
        </div>
        <div class="rec-reason">{{ r.reason }}</div>
        <button class="btn btn-gold btn-sm rec-match-btn" @click="emit('match', (r as any).forMemberId)"><app-icon name="heart" :size="12" />一键牵线</button>
      </div>
    </GlassCard>
  </aside>
</template>

<style scoped>
.right-sidebar { width: 320px; flex-shrink: 0; display: flex; flex-direction: column; gap: 20px; }
.ai-header { padding: 16px; background: var(--dt-glass-dark); border: 1px solid var(--dt-glass-dark-border); color: var(--dt-ink-light); }
.ai-title-row { display: flex; align-items: center; gap: 8px; }
.ai-sparkle { width: 24px; height: 24px; border-radius: 8px; background: var(--dt-gold-gradient); color: #fff; display: flex; align-items: center; justify-content: center; }
.ai-title { font-weight: 700; }
.ai-subtitle { font-size: 0.75rem; color: var(--dt-ink-light-muted); margin-top: 6px; }
.side-card { padding: 16px; }
.side-label { display: flex; align-items: center; gap: 6px; font-size: 0.8125rem; font-weight: 700; color: var(--dt-ink-1); margin-bottom: 12px; }
.todo-item { display: flex; align-items: center; gap: 10px; padding: 8px 0; border-bottom: 1px solid var(--dt-border); }
.todo-item:last-child { border-bottom: none; }
.todo-check { width: 18px; height: 18px; border-radius: 6px; background: var(--dt-primary-soft-2); color: var(--dt-primary); display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.todo-text { flex: 1; font-size: 0.8125rem; color: var(--dt-ink-2); }
.todo-priority { width: 8px; height: 8px; border-radius: 50%; }
.todo-priority.high { background: var(--dt-error); }
.todo-priority.medium { background: var(--dt-warning); }
.todo-priority.low { background: var(--dt-info); }
.rec-item { background: var(--dt-card-solid); border: 1px solid var(--dt-border); border-radius: var(--dt-radius-md); padding: 12px; margin-bottom: 12px; }
.rec-item:last-child { margin-bottom: 0; }
.rec-top { display: flex; gap: 10px; margin-bottom: 8px; }
.rec-avatar { width: 40px; height: 40px; border-radius: 50%; color: #fff; font-weight: 600; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.rec-name { font-weight: 700; color: var(--dt-ink-1); display: flex; align-items: center; gap: 6px; }
.rec-score { font-size: 0.75rem; font-weight: 800; color: var(--dt-gold-dark); background: var(--dt-gold-soft); padding: 1px 6px; border-radius: 999px; }
.rec-detail { font-size: 0.6875rem; color: var(--dt-ink-3); margin-top: 2px; }
.rec-reason { font-size: 0.6875rem; color: var(--dt-ink-3); margin-bottom: 10px; }
.rec-match-btn { width: 100%; }
</style>
