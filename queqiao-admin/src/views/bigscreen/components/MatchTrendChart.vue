<script setup lang="ts">
/** 年度牵线趋势（面积图）— 数据源：screen.matchTrend */
import { computed, inject, ref } from 'vue'
import ScreenPanel from './ScreenPanel.vue'

const screen = inject<any>('screen', ref(null))
const trend = computed<any>(() => screen.value?.matchTrend ?? { months: [], created: [], success: [] })

const trendOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  legend: { data: ['牵线', '成功'], top: 0, right: 8, itemWidth: 12, itemHeight: 8, textStyle: { color: '#9aa1a9', fontSize: 10 } },
  grid: { left: 8, right: 16, top: 26, bottom: 4, containLabel: true },
  xAxis: { type: 'category', data: (trend.value.months ?? []) as any[], axisLabel: { color: '#9aa1a9', fontSize: 10 }, axisLine: { lineStyle: { color: 'rgba(255,255,255,0.15)' } } },
  yAxis: { type: 'value', axisLabel: { color: '#6b7178', fontSize: 10 }, splitLine: { lineStyle: { color: 'rgba(255,255,255,0.06)' } } },
  series: [
    {
      name: '牵线', type: 'line', smooth: true, symbol: 'none',
      data: (trend.value.created ?? []) as any[],
      itemStyle: { color: '#c3a05a' },
      lineStyle: { color: '#c3a05a', width: 3 },
      areaStyle: { color: 'rgba(195,160,90,0.12)' },
    },
    {
      name: '成功', type: 'line', smooth: true, symbol: 'none',
      data: (trend.value.success ?? []) as any[],
      itemStyle: { color: '#8f3a4c' },
      lineStyle: { color: '#8f3a4c', width: 2 },
      areaStyle: { color: 'rgba(143,58,76,0.12)' },
    },
  ],
}))
</script>

<template>
  <ScreenPanel title="年度牵线趋势">
    <div style="height: 180px"><v-chart :option="trendOption" /></div>
  </ScreenPanel>
</template>
