<script setup lang="ts">
/**
 * 牵线详情 · 单方资料卡（头像 + 信息栅格 + 性格标签 + 自我介绍 + 回应状态）
 * 男女双方各渲染一次，通过 person 传入。
 */
import type { PairPerson } from '../types'

defineProps<{ person: PairPerson }>()
</script>

<template>
  <GlassCard class="profile-card" padding="">
    <div class="profile-head">
      <BaseAvatar :name="person.name" :bg="person.avatarBg" :size="64" class="profile-avatar" />
      <div class="profile-title">
        <div class="profile-name">
          {{ person.name }}<span class="profile-age">{{ person.age }}岁</span>
        </div>
        <LevelBadge :level="person.level" />
      </div>
    </div>

    <div class="profile-grid">
      <div v-for="g in person.grid" :key="g.label" class="pg-item">
        <span class="pg-label">{{ g.label }}</span>
        <span class="pg-value">{{ g.value }}</span>
      </div>
    </div>

    <div class="profile-traits">
      <span v-for="t in person.traits" :key="t" class="trait-chip">{{ t }}</span>
    </div>

    <div class="profile-bio">{{ person.bio }}</div>

    <div class="profile-response" :class="person.response.kind">
      <span>{{ person.response.text }}</span>
      <span class="resp-time">{{ person.response.time }}</span>
    </div>
  </GlassCard>
</template>

<style scoped>
.profile-card { flex: 1; padding: 24px; }
.profile-head { display: flex; align-items: center; gap: 14px; margin-bottom: 18px; }
.profile-avatar { font-weight: 500; background: #8d847c !important; }
.profile-name { font-size: 1.25rem; font-weight: 600; color: var(--dt-ink-1); display: flex; align-items: baseline; gap: 8px; }
.profile-age { font-size: 0.875rem; font-weight: 500; color: var(--dt-ink-3); }
.profile-title { display: flex; flex-direction: column; gap: 6px; }
.profile-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; margin-bottom: 16px; }
.pg-item { display: flex; flex-direction: column; }
.pg-label { font-size: 0.6875rem; color: var(--dt-ink-4); }
.pg-value { font-size: 0.8125rem; font-weight: 600; color: var(--dt-ink-1); margin-top: 2px; }
.profile-traits { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 16px; }
.trait-chip { font-size: 0.75rem; padding: 4px 12px; border-radius: var(--dt-radius-sm); background: var(--dt-primary-soft-2); color: var(--dt-primary); font-weight: 500; }
.profile-bio { font-size: 0.8125rem; color: var(--dt-ink-2); line-height: 1.7; font-style: italic; border-left: 2px solid var(--dt-primary); padding-left: 12px; margin-bottom: 16px; }
.profile-response { display: flex; justify-content: space-between; align-items: center; padding: 10px 14px; border-radius: var(--dt-radius-md); font-size: 0.8125rem; font-weight: 600; }
.profile-response.success { background: var(--dt-success-soft); color: var(--dt-success); }
.profile-response.warning { background: var(--dt-warning-soft); color: var(--dt-warning); }
.profile-response .resp-time { font-size: 0.6875rem; font-weight: 400; opacity: 0.8; }
</style>
