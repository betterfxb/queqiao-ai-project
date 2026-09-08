<script setup lang="ts">
/**
 * FinanceStatCards — 财务营收 KPI（全部来自 /api/finance/stats 真实统计）
 */
import { computed } from 'vue'

interface Category { name: string; value: number; percent: number }
interface Stats {
  total?: number
  monthRevenue?: number
  monthCount?: number
  refund?: number
  pending?: number
  orderCount?: number
  avgOrder?: number
  categories?: Category[]
}

const props = defineProps<{ stats: Stats | null; loading?: boolean }>()

const money = (n?: number) => '¥' + Math.round(n ?? 0).toLocaleString()

const kpis = computed(() => {
  const s = props.stats ?? {}
  const cat = (name: string) => s.categories?.find((c) => c.name === name)
  return [
    { label: '本月营收', value: money(s.monthRevenue), sub: `本月 ${s.monthCount ?? 0} 笔订单`, tone: 'primary' },
    { label: '累计营收', value: money(s.total), sub: `共 ${s.orderCount ?? 0} 笔 · 客单价 ${money(s.avgOrder)}`, tone: 'gold' },
    {
      label: '会员续费',
      value: money(cat('会员续费')?.value),
      sub: `占比 ${(cat('会员续费')?.percent ?? 0).toFixed(1)}%`,
      tone: 'info',
    },
    { label: '退款金额', value: money(s.refund), sub: `${s.pending ?? 0} 笔待处理`, tone: 'down' },
  ]
})
</script>

<template>
  <div class="kpi-row" v-loading="loading">
    <div v-for="k in kpis" :key="k.label" class="kpi-card">
      <div class="kpi-info">
        <div class="kpi-label">{{ k.label }}</div>
        <div class="kpi-value" style="font-size: 1.5rem">{{ k.value }}</div>
        <div class="kpi-trend" :class="k.tone === 'down' ? 'down' : 'up'">{{ k.sub }}</div>
      </div>
    </div>
  </div>
</template>
