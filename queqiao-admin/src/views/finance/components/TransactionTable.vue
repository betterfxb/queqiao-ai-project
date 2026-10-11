<script setup lang="ts">
/**
 * TransactionTable — 交易流水（真实订单数据）
 * 默认展示前 12 条，可展开查看全部。
 */
import { computed, ref } from 'vue'
import type { FinanceRecord } from '@/api'

const props = defineProps<{ records: FinanceRecord[] }>()

const showAll = ref(false)
const LIMIT = 12
const shown = computed(() => (showAll.value ? props.records : props.records.slice(0, LIMIT)))

const typeClass = (t: string) =>
  t === '会员续费' ? 'gold' : t === '牵线服务' ? 'primary' : t === '活动收费' ? 'info' : 'warning'
const statusClass = (s: string) =>
  s === '已完成' ? 'success' : s === '已退款' ? 'error' : 'warning'
</script>

<template>
  <div class="glass-card chart-card">
    <div class="section-header">
      <div class="section-title"><span class="section-title-bar" />交易流水</div>
      <div class="section-more" @click="showAll = !showAll">
        {{ showAll ? '收起' : `查看全部 (${records.length})` }}
        <app-icon :name="showAll ? 'chevron-up' : 'chevron-right'" :size="14" />
      </div>
    </div>

    <div v-for="(r, i) in shown" :key="(r as any).id ?? i" class="txn-row">
      <div class="txn-time">{{ r.time }}</div>
      <div class="txn-member">
        {{ r.member }}
        <span v-if="(r as any).agent" class="txn-agent">· {{ (r as any).agent }}</span>
      </div>
      <span class="type-badge" :class="typeClass(r.type)">{{ r.type }}</span>
      <div class="txn-amt" :class="r.amount < 0 ? 'neg' : 'pos'">
        {{ r.amount > 0 ? '+' : '-' }}¥{{ Math.abs(r.amount).toLocaleString() }}
      </div>
      <span class="status-pill" :class="statusClass(r.status)"><span class="dot" />{{ r.status }}</span>
    </div>

    <div v-if="!records.length" class="empty">暂无符合条件的交易记录</div>
  </div>
</template>

<style scoped>
.chart-card { padding: 20px; }
.section-more { cursor: pointer; }
.txn-row { display: flex; align-items: center; gap: 12px; padding: 11px 0; border-bottom: 1px solid var(--dt-border); }
.txn-row:last-child { border-bottom: none; }
.txn-time { font-size: 0.75rem; color: var(--dt-ink-4); width: 96px; flex-shrink: 0; }
.txn-member { font-size: 0.8125rem; font-weight: 600; color: var(--dt-ink-1); flex: 1; min-width: 0; }
.txn-agent { font-size: 0.7188rem; font-weight: 400; color: var(--dt-ink-4); }
.type-badge { font-size: 0.6875rem; font-weight: 600; padding: 3px 10px; border-radius: var(--dt-radius-sm); white-space: nowrap; }
.type-badge.gold { background: var(--dt-gold-soft); color: var(--dt-gold-dark); }
.type-badge.primary { background: var(--dt-primary-soft-2); color: var(--dt-primary); }
.type-badge.info { background: var(--dt-info-soft); color: var(--dt-info); }
.type-badge.warning { background: var(--dt-warning-soft); color: var(--dt-warning); }
.txn-amt { font-size: 0.875rem; font-weight: 600; width: 100px; text-align: right; flex-shrink: 0; }
.txn-amt.pos { color: var(--dt-success); }
.txn-amt.neg { color: var(--dt-error); }
.empty { color: var(--dt-ink-3); font-size: 0.8125rem; padding: 20px 0; text-align: center; }
</style>
