<script setup lang="ts">
/** 性别环形 + 年龄分布柱状 — 数据源：screen.genderAge */
import { computed, inject, ref } from 'vue'
import ScreenPanel from './ScreenPanel.vue'

const screen = inject<any>('screen', ref(null))

const ga = computed<any>(() => screen.value?.genderAge ?? { buckets: [], male: [], female: [] })
const sum = (arr: any): number => ((arr ?? []) as any[]).reduce((a: number, b: any) => a + Number(b || 0), 0)

const maleTotal = computed(() => sum(ga.value.male))
const femaleTotal = computed(() => sum(ga.value.female))
const total = computed(() => maleTotal.value + femaleTotal.value)
const pct = (n: number) => (total.value ? Math.round((n / total.value) * 100) : 0)

/* 性别环形 */
const genderOption = computed(() => ({
  tooltip: { trigger: 'item' },
  series: [{
    type: 'pie', radius: ['62%', '82%'],
    label: { show: false }, itemStyle: { borderColor: '#14161a', borderWidth: 2 },
    data: [
      { value: maleTotal.value, name: `男 ${pct(maleTotal.value)}%`, itemStyle: { color: '#8f3a4c' } },
      { value: femaleTotal.value, name: `女 ${pct(femaleTotal.value)}%`, itemStyle: { color: '#c3a05a' } },
    ],
  }],
}))

/* 年龄分布柱状：按年龄段男女堆叠 */
const ageOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  grid: { left: 8, right: 8, top: 20, bottom: 4, containLabel: true },
  xAxis: { type: 'category', data: (ga.value.buckets ?? []) as any[], axisLabel: { color: '#9aa1a9', fontSize: 11 }, axisLine: { show: false }, axisTick: { show: false } },
  yAxis: { show: false },
  series: [
    {
      name: '男', type: 'bar', stack: 'age', barWidth: 20,
      data: (ga.value.male ?? []) as any[],
      itemStyle: { color: '#8f3a4c' },
    },
    {
      name: '女', type: 'bar', stack: 'age', barWidth: 20,
      data: (ga.value.female ?? []) as any[],
      label: { show: true, position: 'top', color: '#c3a05a', fontSize: 11, formatter: (p: any) => String(Number((ga.value.male ?? [])[p.dataIndex] || 0) + Number((ga.value.female ?? [])[p.dataIndex] || 0)) },
      itemStyle: { borderRadius: [4, 4, 0, 0], color: '#c3a05a' },
    },
  ],
}))
</script>

<template>
  <ScreenPanel title="性别 / 年龄分布">
    <div class="gender-age">
      <div class="gender-pie"><v-chart :option="genderOption" /><div class="pie-mid">{{ total }}</div></div>
      <div class="age-bar"><v-chart :option="ageOption" /></div>
    </div>
  </ScreenPanel>
</template>

<style scoped>
.gender-age { display: grid; grid-template-columns: 1fr 1.2fr; gap: 10px; height: 150px; }
.gender-pie { position: relative; }
.pie-mid { position: absolute; top: 50%; left: 50%; transform: translate(-50%, -50%); font-size: 1.125rem; font-weight: 700; color: var(--bs-gold); font-variant-numeric: tabular-nums; }
</style>
