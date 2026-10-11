<script setup lang="ts">
/** 牵线详情 · AI 匹配分析报告（维度进度条 + 综合评语） */
import type { AiBar } from '../types'

defineProps<{ bars: AiBar[]; comment: string }>()
</script>

<template>
  <GlassCard class="ai-card" padding="">
    <div class="section-title"><span class="section-title-bar" />AI 匹配分析报告</div>
    <div class="ai-bars">
      <div v-for="b in bars" :key="b.label" class="ai-bar">
        <span class="ai-bar-label">{{ b.label }}</span>
        <div class="ai-bar-track"><div class="ai-bar-fill" :style="{ width: b.pct + '%' }" /></div>
        <span class="ai-bar-pct">{{ b.pct }}%</span>
      </div>
    </div>
    <div class="ai-comment">
      <b>AI 综合评语：</b>{{ comment }}
    </div>
  </GlassCard>
</template>

<style scoped>
.ai-card { padding: 24px; }
.ai-bars { display: flex; flex-direction: column; gap: 14px; margin: 18px 0; }
.ai-bar { display: flex; align-items: center; gap: 12px; }
.ai-bar-label { width: 70px; font-size: 0.8125rem; color: var(--dt-ink-2); flex-shrink: 0; }
.ai-bar-track { flex: 1; height: 8px; border-radius: var(--dt-radius-sm); background: var(--dt-muted); overflow: hidden; }
.ai-bar-fill { height: 100%; border-radius: var(--dt-radius-sm); background: var(--dt-primary); }
.ai-bar-pct { width: 40px; text-align: right; font-size: 0.8125rem; font-weight: 600; color: var(--dt-primary); flex-shrink: 0; }
.ai-comment { font-size: 0.8125rem; color: var(--dt-ink-2); line-height: 1.7; background: var(--dt-muted); border-left: 2px solid var(--dt-primary); border-radius: var(--dt-radius-md); padding: 14px 16px; }
.ai-comment b { color: var(--dt-primary); }
</style>
