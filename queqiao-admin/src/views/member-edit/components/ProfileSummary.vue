<script setup lang="ts">
/** 会员档案编辑 · 左侧会员摘要卡 */
defineProps<{ name: string; meta: string; levelText: string; agent: string }>()
</script>

<template>
  <GlassCard class="summary-card" padding="">
    <BaseAvatar :name="name" :size="76" class="summary-avatar" />
    <div class="summary-name">{{ name }}</div>
    <div class="summary-meta">{{ meta }}</div>
    <span class="level-badge vip" style="margin-top: 10px">{{ levelText }}</span>
    <div class="summary-agent">专属红娘：<b>{{ agent }}</b></div>
  </GlassCard>
</template>

<style scoped>
.summary-card { padding: 28px; display: flex; flex-direction: column; align-items: center; text-align: center; }
.summary-avatar {
  font-weight: 700;
  border: 3px solid var(--dt-gold); box-shadow: var(--dt-shadow-gold-glow);
}
.summary-name { font-size: 1.375rem; font-weight: 800; margin-top: 14px; }
.summary-meta { font-size: 0.8125rem; color: var(--dt-ink-3); margin-top: 4px; }
.summary-agent { font-size: 0.8125rem; color: var(--dt-ink-3); margin-top: 14px; }
.summary-agent b { color: var(--dt-primary); }
</style>
