<script setup lang="ts">
/** 会员满意度仪表盘 — 数据源：screen.satisfaction */
import { computed, inject, ref } from 'vue'
import ScreenPanel from './ScreenPanel.vue'

const screen = inject<any>('screen', ref(null))
const sat = computed<any>(() => screen.value?.satisfaction ?? {})

const score = computed(() => Number(sat.value.score ?? 0))
const rating = computed(() => Number(sat.value.rating ?? 0))
const successRate = computed(() => sat.value.successRate)
const stars = computed(() => {
  const n = Math.max(0, Math.min(5, Math.round(rating.value)))
  return '★'.repeat(n) + '☆'.repeat(5 - n)
})

const gaugeOption = computed(() => ({
  series: [{
    type: 'gauge', startAngle: 200, endAngle: -20, min: 0, max: 100, radius: '100%',
    progress: { show: true, width: 12, itemStyle: { color: '#c3a05a' } },
    axisLine: { lineStyle: { width: 12, color: [[1, 'rgba(255,255,255,0.1)']] } },
    axisTick: { show: false }, splitLine: { show: false }, axisLabel: { show: false }, pointer: { show: false },
    detail: { valueAnimation: true, fontSize: 30, color: '#c3a05a', offsetCenter: [0, '-10%'], formatter: '{value}%' },
    data: [{ value: score.value }],
  }],
}))
</script>

<template>
  <ScreenPanel title="会员满意度">
    <div style="height: 140px"><v-chart :option="gaugeOption" /></div>
    <div class="gauge-stars">
      {{ stars }}
      <span>{{ rating.toFixed(1) }}/5.0</span>
      <span v-if="successRate != null">成功率 {{ successRate }}%</span>
    </div>
  </ScreenPanel>
</template>

<style scoped>
.gauge-stars { text-align: center; color: var(--bs-gold); font-size: 1rem; letter-spacing: 2px; margin-top: -18px; font-variant-numeric: tabular-nums; }
.gauge-stars span { font-size: 0.75rem; color: #9aa1a9; margin-left: 6px; }
</style>
