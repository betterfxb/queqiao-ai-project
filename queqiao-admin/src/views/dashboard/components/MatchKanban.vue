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
.kanban-board { display: grid; grid-template-columns: repeat(3, 1fr); gap: 20px; }
.kanban-column { padding: 16px; }
.kanban-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 14px; }
.kanban-title-wrap { display: flex; align-items: center; gap: 8px; }
.kanban-title { font-weight: 700; color: var(--dt-ink-1); }
.kanban-count {
  background: var(--dt-primary-soft-2); color: var(--dt-primary);
  font-size: 0.75rem; font-weight: 700; padding: 1px 8px; border-radius: 999px;
}
.kanban-cards { display: flex; flex-direction: column; gap: 14px; }
.match-card {
  background: var(--dt-card-solid); border: 1px solid var(--dt-border);
  border-radius: var(--dt-radius-md); padding: 14px; cursor: pointer;
  transition: transform 0.15s ease, box-shadow 0.15s ease;
}
.match-card:hover { transform: translateY(-2px); box-shadow: var(--dt-shadow-md); }
.match-avatars { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; }
.avatar-pair { display: flex; align-items: center; }
.person-avatar {
  width: 40px; height: 40px; border-radius: 50%; color: #fff; font-weight: 600; font-size: 0.9375rem;
  display: inline-flex; align-items: center; justify-content: center; border: 2px solid #fff;
}
.person-avatar.male { background: linear-gradient(135deg, #b8c4d0, #5a7a9a); }
.person-avatar.female { background: linear-gradient(135deg, #f4c6d0, #a8667d); margin-left: -6px; }
.avatar-connector { position: relative; width: 0; }
.heart-dot {
  position: absolute; left: -8px; top: -8px; width: 16px; height: 16px; border-radius: 50%;
  background: var(--dt-gold-gradient); color: #fff; display: flex; align-items: center; justify-content: center;
  box-shadow: 0 2px 6px rgba(168, 138, 80, 0.4); z-index: 2;
}
.match-ring { width: 52px; height: 52px; border-radius: 50%; display: flex; align-items: center; justify-content: center; }
.match-ring-inner {
  width: 40px; height: 40px; border-radius: 50%; background: #fff;
  display: flex; flex-direction: column; align-items: center; justify-content: center;
}
.match-ring-pct { font-size: 0.8125rem; font-weight: 800; color: var(--dt-gold-dark); line-height: 1; }
.match-ring-label { font-size: 0.5625rem; color: var(--dt-ink-4); }
.person-info { display: flex; justify-content: space-between; margin-bottom: 10px; }
.person-name { font-weight: 700; color: var(--dt-ink-1); font-size: 0.875rem; }
.person-age { font-size: 0.6875rem; color: var(--dt-ink-3); }
.match-tag { font-size: 0.6875rem; font-weight: 600; padding: 3px 10px; border-radius: 999px; display: inline-block; margin-bottom: 10px; }
.match-tag.gold { background: var(--dt-gold-soft); color: var(--dt-gold-dark); }
.match-tag.burgundy { background: var(--dt-primary-soft-2); color: var(--dt-primary); }
.match-footer { display: flex; justify-content: space-between; font-size: 0.6875rem; color: var(--dt-ink-4); }
.matchmaker-name { display: inline-flex; align-items: center; gap: 4px; color: var(--dt-primary); }
</style>
