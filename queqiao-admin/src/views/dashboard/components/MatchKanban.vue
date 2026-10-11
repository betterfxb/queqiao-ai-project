<script setup lang="ts">
/** Dashboard · 牵线看板（牵线中 / 待反馈 / 已约见 三列 + 匹配卡片） */
import { matchRingBg } from '@/utils/member'
import type { KanbanColumn } from '../types'

defineProps<{ columns: KanbanColumn[] }>()
const emit = defineEmits<{ (e: 'open'): void }>()
</script>

<template>
  <div class="kanban-board">
    <GlassCard v-for="col in columns" :key="col.title" class="kanban-column" padding="">
      <div class="kanban-header">
        <div class="kanban-title-wrap">
          <span class="kanban-title">{{ col.title }}</span>
          <span class="kanban-count">{{ col.count }}</span>
        </div>
        <app-icon name="more" :size="16" class="text-muted" />
      </div>
      <div class="kanban-cards">
        <div v-for="(c, i) in col.cards" :key="i" class="match-card" @click="emit('open')">
          <div class="match-avatars">
            <div class="avatar-pair">
              <div class="person-avatar male">{{ c.male[0] }}</div>
              <div class="avatar-connector"><div class="heart-dot"><app-icon name="heart" :size="9" /></div></div>
              <div class="person-avatar female">{{ c.female[0] }}</div>
            </div>
            <div class="match-ring-wrap">
              <div class="match-ring" :style="{ background: matchRingBg(c.score) }">
                <div class="match-ring-inner">
                  <span class="match-ring-pct">{{ c.score }}%</span>
                  <span class="match-ring-label">匹配</span>
                </div>
              </div>
            </div>
          </div>
          <div class="person-info">
            <div class="person-block">
              <div class="person-name">{{ c.male }}</div>
              <div class="person-age">{{ c.maleInfo }}</div>
            </div>
            <div class="person-block">
              <div class="person-name">{{ c.female }}</div>
              <div class="person-age">{{ c.femaleInfo }}</div>
            </div>
          </div>
          <div class="match-tag" :class="c.tagKind">{{ c.tag }}</div>
          <div class="match-footer">
            <span class="matchmaker-name"><app-icon name="heart" :size="10" />红娘：{{ c.agent }}</span>
            <span class="match-time">{{ c.time }}</span>
          </div>
        </div>
      </div>
    </GlassCard>
  </div>
</template>

<style scoped>
.kanban-board { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px; }
.kanban-column { padding: 14px 16px; }
.kanban-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; }
.kanban-title-wrap { display: flex; align-items: center; gap: 8px; }
.kanban-title { font-weight: 600; color: var(--dt-ink-1); font-size: 0.875rem; }
.kanban-count {
  background: var(--dt-muted); color: var(--dt-ink-2);
  font-size: 0.75rem; font-weight: 500; padding: 0 6px; border-radius: var(--dt-radius-sm);
}
.kanban-cards { display: flex; flex-direction: column; gap: 10px; }
.match-card {
  background: var(--dt-card-solid); border: 1px solid var(--dt-border);
  border-radius: var(--dt-radius-md); padding: 13px 14px; cursor: pointer;
  transition: border-color 0.15s ease;
}
.match-card:hover { border-color: var(--dt-border-strong); }
.match-avatars { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; }
.avatar-pair { display: flex; align-items: center; }
.person-avatar {
  width: 36px; height: 36px; border-radius: 50%; color: #fff; font-weight: 500; font-size: 0.875rem;
  display: inline-flex; align-items: center; justify-content: center; border: 2px solid var(--dt-card-solid);
}
.person-avatar.male { background: #4f6d8f; }
.person-avatar.female { background: #a8607a; margin-left: -6px; }
.avatar-connector { position: relative; width: 0; }
.heart-dot {
  position: absolute; left: -7px; top: -7px; width: 14px; height: 14px; border-radius: 50%;
  background: var(--dt-primary); color: #fff; display: flex; align-items: center; justify-content: center;
  z-index: 2;
}
.match-ring { width: 46px; height: 46px; border-radius: 50%; display: flex; align-items: center; justify-content: center; }
.match-ring-inner {
  width: 36px; height: 36px; border-radius: 50%; background: var(--dt-card-solid);
  display: flex; flex-direction: column; align-items: center; justify-content: center;
}
.match-ring-pct { font-size: 0.75rem; font-weight: 600; color: var(--dt-ink-1); line-height: 1; font-variant-numeric: tabular-nums; }
.match-ring-label { font-size: 0.5625rem; color: var(--dt-ink-4); }
.person-info { display: flex; justify-content: space-between; margin-bottom: 10px; }
.person-name { font-weight: 600; color: var(--dt-ink-1); font-size: 0.8125rem; }
.person-age { font-size: 0.6875rem; color: var(--dt-ink-3); }
.match-tag { font-size: 0.6875rem; font-weight: 500; padding: 2px 7px; border-radius: var(--dt-radius-sm); display: inline-block; margin-bottom: 10px; }
.match-tag.gold { background: var(--dt-gold-soft); color: var(--dt-gold-dark); }
.match-tag.burgundy { background: var(--dt-primary-soft-2); color: var(--dt-primary); }
.match-footer { display: flex; justify-content: space-between; font-size: 0.6875rem; color: var(--dt-ink-4); }
.matchmaker-name { display: inline-flex; align-items: center; gap: 4px; color: var(--dt-ink-3); }
</style>
