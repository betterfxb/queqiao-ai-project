<script setup lang="ts">
/** 牵线详情 · 状态横幅：牵线状态 + 环形匹配度 + 阶段 stepper（点击可推进阶段） */
import { computed } from 'vue'
import { matchRingBg } from '@/utils/member'
import type { MatchStep } from '../types'

const props = defineProps<{
  score: number
  orderNo: string
  createdAt: string
  steps: MatchStep[]
  status?: string
}>()
const emit = defineEmits<{ (e: 'stage', label: string): void }>()

const scoreLabel = computed(() =>
  props.score >= 90 ? '极高匹配' : props.score >= 80 ? '高匹配' : props.score >= 70 ? '较匹配' : '一般匹配',
)
</script>

<template>
  <GlassCard class="banner" padding="">
    <div class="banner-left">
      <!-- 保留原生 span：pulse 动效需要作用到内部 .dot -->
      <span class="status-pill gold pulse"><span class="dot" />{{ status || '牵线中' }}</span>
      <div class="banner-no">单号 {{ orderNo }}</div>
      <div class="banner-date">创建于 {{ createdAt || '—' }}</div>
    </div>
    <div class="banner-ring">
      <div class="big-ring" :style="{ background: matchRingBg(score) }">
        <div class="big-ring-inner">
          <span class="big-ring-pct">{{ score }}%</span>
          <span class="big-ring-label">匹配度</span>
        </div>
      </div>
      <div class="ring-text">
        <div class="ring-title">AI 智能匹配</div>
        <span class="level-badge vip">极高匹配</span>
      </div>
    </div>
    <div class="banner-steps">
      <div v-for="(s, i) in steps" :key="s.label" class="step" :class="s.state">
        <div class="step-dot">
          <app-icon v-if="s.state === 'done'" name="check" :size="12" />
          <span v-else>{{ i + 1 }}</span>
        </div>
        <span class="step-label">{{ s.label }}</span>
        <div v-if="i < steps.length - 1" class="step-line" :class="{ done: steps[i + 1].state !== 'pending' }" />
      </div>
    </div>
  </GlassCard>
</template>

<style scoped>
.banner { display: flex; align-items: center; gap: 40px; padding: 24px 32px; }
.banner-no { font-size: 1.125rem; font-weight: 600; color: var(--dt-ink-1); margin-top: 10px; }
.banner-date { font-size: 0.75rem; color: var(--dt-ink-3); margin-top: 4px; }
.status-pill.pulse .dot { animation: pulse 1.4s infinite; }
@keyframes pulse { 0%,100% { opacity: 1; } 50% { opacity: 0.3; } }
.banner-ring { display: flex; align-items: center; gap: 18px; }
.big-ring { width: 110px; height: 110px; border-radius: 50%; display: flex; align-items: center; justify-content: center; }
.big-ring-inner { width: 88px; height: 88px; border-radius: 50%; background: var(--dt-card-solid); display: flex; flex-direction: column; align-items: center; justify-content: center; }
.big-ring-pct { font-size: 1.625rem; font-weight: 600; color: var(--dt-primary); line-height: 1; }
.big-ring-label { font-size: 0.6875rem; color: var(--dt-ink-4); }
.ring-title { font-size: 0.9375rem; font-weight: 600; color: var(--dt-ink-1); margin-bottom: 8px; }
.banner-steps { margin-left: auto; display: flex; align-items: center; gap: 0; }
.step { position: relative; display: flex; flex-direction: column; align-items: center; padding: 0 22px; }
.step-dot { width: 30px; height: 30px; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-size: 0.8125rem; font-weight: 600; background: var(--dt-muted); color: var(--dt-ink-3); }
.step.done .step-dot { background: var(--dt-success); color: #fff; }
.step.active .step-dot { background: var(--dt-primary); color: #fff; }
.step-label { font-size: 0.75rem; color: var(--dt-ink-3); margin-top: 8px; white-space: nowrap; }
.step.active .step-label { color: var(--dt-primary); font-weight: 600; }
.step-line { position: absolute; top: 15px; left: 50%; width: 100%; height: 2px; background: var(--dt-border); z-index: -1; }
.step-line.done { background: var(--dt-primary); }
</style>
