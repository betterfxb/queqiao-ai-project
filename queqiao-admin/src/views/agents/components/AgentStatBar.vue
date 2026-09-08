<script setup lang="ts">
/**
 * AgentStatBar — 红娘团队顶部 KPI 统计条
 * 数据来自真实接口 /agents/stats（fetchAgentStats）；后端不可用时显示占位，
 * 不再写死任何数字，避免误导运营决策。
 * 样式全部来自全局 .kpi-*（src/styles/base.css），此处无需 scoped 样式。
 */
import { onMounted, ref } from 'vue'
import { fetchAgentStats } from '@/api'

interface Kpi {
  label: string
  value: string
  unit: string
  trend: string
  tone: string
  up: boolean
}

const kpis = ref<Kpi[]>([])
const failed = ref(false)

/** 把后端返回的统计对象映射为卡片；字段缺失时显示「—」而非编造 */
function build(s: Record<string, unknown>): Kpi[] {
  const num = (v: unknown) => (v === undefined || v === null || v === '' ? '—' : String(v))
  const total = num(s.total ?? s.agentTotal ?? s.count)
  const active = num(s.active ?? s.online ?? s.activeCount)
  const rate = num(s.avgSuccessRate ?? s.successRate ?? s.rate)
  const top = s.topAgent ?? s.goldAgent ?? s.topAgentName
  return [
    { label: '红娘总数', value: total, unit: '人', trend: '实时统计', tone: 'gold', up: true },
    { label: '在职红娘', value: active, unit: '人', trend: '在岗统计', tone: 'burgundy', up: true },
    { label: '平均成功率', value: rate, unit: '%', trend: '实时统计', tone: 'gold', up: true },
    {
      label: '本月金牌红娘',
      value: top ? String(top) : '—',
      unit: '',
      trend: top ? '业绩领先' : '待数据',
      tone: 'warning',
      up: true,
    },
  ]
}

onMounted(async () => {
  try {
    const s = (await fetchAgentStats()) as Record<string, unknown>
    kpis.value = build(s)
  } catch {
    // 后端不可用时保持空占位，绝不回退到写死的数字
    failed.value = true
  }
})
</script>

<template>
  <div v-if="kpis.length" class="kpi-row">
    <div v-for="k in kpis" :key="k.label" class="kpi-card">
      <div class="kpi-info">
        <div class="kpi-label">{{ k.label }}</div>
        <div class="kpi-value" :style="k.unit ? {} : { fontSize: '1.375rem' }">
          {{ k.value }}<span v-if="k.unit" class="unit">{{ k.unit }}</span>
        </div>
        <div class="kpi-trend" :class="k.up ? 'up' : 'down'">{{ k.trend }}</div>
      </div>
      <div class="kpi-icon" :class="k.tone">
        <app-icon :name="k.tone === 'warning' ? 'crown' : 'users'" :size="22" />
      </div>
    </div>
  </div>
  <div v-else class="kpi-row kpi-empty">
    <div class="kpi-card placeholder">
      <div class="kpi-info">
        <div class="kpi-label">团队统计</div>
        <div class="kpi-value" style="font-size: 1.125rem">{{ failed ? '接口暂不可用' : '加载中…' }}</div>
        <div class="kpi-trend">数据来源：真实接口 /agents/stats</div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.kpi-empty { opacity: 0.85; }
.kpi-empty .placeholder { border-style: dashed; }
</style>
