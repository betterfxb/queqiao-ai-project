<script setup lang="ts">
/** 年度牵线趋势（面积图）— 数据源：screen.matchTrend */
import { computed, inject, ref } from 'vue'
import ScreenPanel from './ScreenPanel.vue'

const screen = inject<any>('screen', ref(null))
const trend = computed<any>(() => screen.value?.matchTrend ?? { months: [], created: [], success: [] })

const trendOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  legend: { data: ['牵线', '成功'], top: 0, right: 8, itemWidth: 12, itemHeight: 8, textStyle: { color: 'rgba(240,230,210,0.7)', fontSize: 10 } },
  grid: { left: 8, right: 16, top: 26, bottom: 4, containLabel: true },
  xAxis: { type: 'category', data: (trend.value.months ?? []) as any[], axisLabel: { color: '#f0e6d2', fontSize: 10 }, axisLine: { lineStyle: { color: 'rgba(240,230,210,0.15)' } } },
  yAxis: { type: 'value', axisLabel: { color: 'rgba(240,230,210,0.5)', fontSize: 10 }, splitLine: { lineStyle: { color: 'rgba(240,230,210,0.06)' } } },
  series: [
    {
      name: '牵线', type: 'line', smooth: true, symbol: 'none',
      data: (trend.value.created ?? []) as any[],
      itemStyle: { color: '#e8c878' },
      lineStyle: { color: '#e8c878', width: 3 },
      areaStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [{ offset: 0, color: 'rgba(232,200,120,0.35)' }, { offset: 1, color: 'rgba(232,200,120,0)' }] } },
    },
    {
      name: '成功', type: 'line', smooth: true, symbol: 'none',
      data: (trend.value.success ?? []) as any[],
      itemStyle: { color: '#9a3349' },
      lineStyle: { color: '#9a3349', width: 2 },
      areaStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [{ offset: 0, color: 'rgba(154,51,73,0.3)' }, { offset: 1, color: 'rgba(154,51,73,0)' }] } },
    },
  ],
}))
</script>

<template>
  <ScreenPanel title="年度牵线趋势">
    <div style="height: 180px"><v-chart :option="trendOption" /></div>
  </ScreenPanel>
</template>
