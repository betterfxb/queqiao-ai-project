<script setup lang="ts">
/**
 * AgentCard — 单个红娘卡片（头像 + 等级 + 评分 + 四项业绩 + 操作按钮）
 */
import type { Agent } from '@/api'

defineProps<{ agent: Agent }>()
const emit = defineEmits<{ (e: 'action', payload: { label: string; agent: Agent }): void }>()

/** 红娘等级 → 全局 .level-badge 变体（金牌=vip / 资深=premium / 普通=standard） */
const levelClass = (l: string) => (l === '金牌红娘' ? 'vip' : l === '资深红娘' ? 'premium' : 'standard')
const statusDot = (s: string) => (s === '在职' ? 'var(--dt-success)' : s === '休假' ? 'var(--dt-warning)' : 'var(--dt-ink-4)')
</script>

<template>
  <div class="glass-card agent-card" :class="{ off: agent.status !== '在职' }">
    <div class="agent-head">
      <BaseAvatar class="agent-avatar" :size="48" :bg="agent.avatarBg">
        {{ agent.name[0] }}
        <span class="agent-status" :style="{ background: statusDot(agent.status) }" />
      </BaseAvatar>
      <div class="agent-title">
        <div class="agent-name">{{ agent.name }} <span class="agent-id">{{ agent.id }}</span></div>
        <span class="level-badge" :class="levelClass(agent.level)">{{ agent.level }}</span>
      </div>
      <div class="agent-rating">
        <div class="stars">
          <app-icon v-for="i in 5" :key="i" name="star" :size="13" :style="{ color: i <= Math.round(agent.rating) ? 'var(--dt-gold)' : 'var(--dt-ink-4)' }" />
        </div>
        <span class="rating-num">{{ agent.rating }}</span>
      </div>
    </div>
    <div class="agent-stats">
      <div class="as-item"><div class="as-num">{{ agent.memberCount }}</div><div class="as-label">负责会员</div></div>
      <div class="as-item"><div class="as-num">{{ agent.monthMatch }}</div><div class="as-label">本月牵线</div></div>
      <div class="as-item"><div class="as-num">{{ agent.successRate }}%</div><div class="as-label">成功率</div></div>
      <div class="as-item"><div class="as-num gold">¥{{ agent.monthRevenue.toLocaleString() }}</div><div class="as-label">本月业绩</div></div>
    </div>
    <div class="agent-actions">
      <button class="btn btn-sm btn-outline" @click="emit('action', { label: '查看详情', agent })">查看详情</button>
      <button class="btn btn-sm btn-outline" @click="emit('action', { label: '分配会员', agent })">分配会员</button>
      <button class="btn btn-sm btn-outline" @click="emit('action', { label: '业绩报表', agent })">业绩报表</button>
    </div>
  </div>
</template>

<style scoped>
.agent-card { padding: 18px; transition: all 0.15s; }
.agent-card.off { opacity: 0.7; }
.agent-card:hover { transform: translateY(-2px); box-shadow: var(--dt-shadow-lg); }
.agent-head { display: flex; align-items: center; gap: 12px; margin-bottom: 14px; }
/* BaseAvatar 基础样式来自全局 .app-avatar，这里补齐红娘卡的定位与字重（字号需覆盖组件内联样式） */
.agent-avatar { position: relative; font-weight: 700; font-size: 1.125rem !important; }
.agent-status { position: absolute; bottom: 0; right: 0; width: 12px; height: 12px; border-radius: 50%; border: 2px solid #fff; }
.agent-title { flex: 1; min-width: 0; }
.agent-name { font-weight: 700; color: var(--dt-ink-1); font-size: 0.9375rem; display: flex; align-items: center; gap: 8px; }
.agent-id { font-size: 0.6875rem; color: var(--dt-ink-4); font-weight: 400; }
.agent-rating { display: flex; flex-direction: column; align-items: flex-end; gap: 2px; }
.stars { display: flex; gap: 1px; }
.rating-num { font-size: 0.75rem; font-weight: 700; color: var(--dt-gold-dark); }
.agent-stats { display: grid; grid-template-columns: repeat(4, 1fr); gap: 8px; padding: 12px 0; border-top: 1px solid var(--dt-border); border-bottom: 1px solid var(--dt-border); margin-bottom: 12px; }
.as-item { text-align: center; }
.as-num { font-size: 1rem; font-weight: 800; color: var(--dt-ink-1); }
.as-num.gold { color: var(--dt-gold-dark); }
.as-label { font-size: 0.6875rem; color: var(--dt-ink-4); margin-top: 2px; }
.agent-actions { display: flex; gap: 8px; }
.agent-actions .btn { flex: 1; }
</style>
