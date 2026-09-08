<script setup lang="ts">
/**
 * AuditCard — 审核队列中的单条待办卡（类型 / 申请人 / 紧急程度 / 变更或支付信息 / 操作）
 */
import type { AuditItem } from '@/api'

withDefaults(
  defineProps<{ item: AuditItem; selected?: boolean; passed?: boolean }>(),
  { selected: false, passed: false },
)
const emit = defineEmits<{
  (e: 'select'): void
  (e: 'reject'): void
  (e: 'approve'): void
}>()

const typeTone = (t: AuditItem['type']) =>
  t === '实名认证' ? 'primary' : t === '资料修改' ? 'info' : t === '会员升级' ? 'gold' : 'success'
const urgencyTone = (u: AuditItem['urgency']) => (u === '紧急' ? 'error' : u === '普通' ? 'warning' : 'neutral')
</script>

<template>
  <div
    class="glass-card audit-item" :class="{ selected, passed }"
    @click="emit('select')"
  >
    <div class="ai-item-head">
      <span class="type-badge" :class="typeTone(item.type)">{{ item.type }}</span>
      <span class="ai-item-time">{{ item.timeAgo }}</span>
      <span class="status-pill" :class="urgencyTone(item.urgency)">{{ item.urgency }}</span>
    </div>
    <div class="ai-item-person">
      <div class="table-avatar" :style="{ background: 'linear-gradient(135deg,#B8C4D0,#5A7A9A)' }">{{ item.applicant[0] }}</div>
      <div>
        <div class="ai-item-name">{{ item.applicant }} <span class="ga-tag">{{ item.gender }}·{{ item.age }}</span></div>
        <div class="ai-item-sub">{{ item.memberId }} · 提交红娘 {{ item.agent }}</div>
      </div>
    </div>
    <div v-if="item.change" class="ai-item-change">
      {{ item.change.field }}：<s>{{ item.change.from }}</s> → <b>{{ item.change.to }}</b>
    </div>
    <div v-if="item.pay" class="ai-item-pay" :class="{ paid: item.pay.paid }">{{ item.pay.text }}</div>
    <div class="ai-item-actions" @click.stop>
      <button class="btn btn-sm btn-ghost" @click="emit('select')">查看详情</button>
      <button class="btn btn-sm btn-danger" @click="emit('reject')">驳回</button>
      <button class="btn btn-sm btn-primary" @click="emit('approve')">通过</button>
    </div>
  </div>
</template>

<style scoped>
.audit-item { padding: 16px 18px; cursor: pointer; transition: all 0.15s; border: 1px solid var(--dt-border); }
.audit-item:hover { transform: translateY(-1px); box-shadow: var(--dt-shadow-lg); }
.audit-item.selected { border-color: var(--dt-gold); box-shadow: var(--dt-shadow-gold-glow); }
.audit-item.passed { opacity: 0.5; }
.ai-item-head { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; }
.type-badge { font-size: 0.75rem; font-weight: 700; padding: 3px 10px; border-radius: 999px; }
.type-badge.primary { background: var(--dt-primary-soft-2); color: var(--dt-primary); }
.type-badge.info { background: var(--dt-info-soft); color: var(--dt-info); }
.type-badge.gold { background: var(--dt-gold-soft); color: var(--dt-gold-dark); }
.type-badge.success { background: var(--dt-success-soft); color: var(--dt-success); }
.ai-item-time { font-size: 0.75rem; color: var(--dt-ink-4); }
.ai-item-head .status-pill { margin-left: auto; }
.ai-item-person { display: flex; align-items: center; gap: 10px; margin-bottom: 10px; }
.table-avatar { width: 36px; height: 36px; border-radius: 50%; color: #fff; font-weight: 600; display: inline-flex; align-items: center; justify-content: center; flex-shrink: 0; font-size: 0.875rem; }
.ai-item-name { font-weight: 700; color: var(--dt-ink-1); display: flex; align-items: center; gap: 8px; }
.ga-tag { font-size: 0.6875rem; font-weight: 500; color: var(--dt-ink-3); background: var(--dt-muted); padding: 1px 8px; border-radius: 999px; }
.ai-item-sub { font-size: 0.75rem; color: var(--dt-ink-4); }
.ai-item-change { font-size: 0.8125rem; color: var(--dt-ink-2); background: var(--dt-muted); border-radius: 8px; padding: 8px 12px; margin-bottom: 10px; }
.ai-item-change s { color: var(--dt-ink-4); }
.ai-item-change b { color: var(--dt-gold-dark); }
.ai-item-pay { display: inline-block; font-size: 0.75rem; font-weight: 700; padding: 3px 10px; border-radius: 999px; background: var(--dt-warning-soft); color: var(--dt-warning); margin-bottom: 10px; }
.ai-item-pay.paid { background: var(--dt-success-soft); color: var(--dt-success); }
.ai-item-actions { display: flex; gap: 8px; justify-content: flex-end; }
</style>
