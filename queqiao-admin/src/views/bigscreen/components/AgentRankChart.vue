<script setup lang="ts">
/** 红娘业绩 TOP5 横向条 — 数据源：screen.agentRank */
import { computed, inject, ref } from 'vue'
import ScreenPanel from './ScreenPanel.vue'

const screen = inject<any>('screen', ref(null))

/** ECharts 类目轴自下而上绘制，倒序后榜首显示在最上方 */
const rank = computed<any[]>(() => ((screen.value?.agentRank ?? []) as any[]).slice().reverse())

const fmt = (v: number) =>
  v >= 10000 ? '¥' + (v / 10000).toFixed(1) + '万' : v >= 1000 ? '¥' + (v / 1000).toFixed(1) + 'k' : String(v)

const agentOption = computed(() => ({
  tooltip: {
    trigger: 'item',
    formatter: (p: any) => {
      const a = rank.value[p.dataIndex] ?? {}
      return [
        `${a.name ?? ''}${a.level ? ' · ' + a.level : ''}`,
        `业绩：${fmt(Number(a.value || 0))}`,
        a.match != null ? `牵线：${a.match}` : '',
        a.successRate != null ? `成功率：${a.successRate}%` : '',
      ].filter(Boolean).join('<br/>')
    },
  },
  grid: { left: 8, right: 50, top: 8, bottom: 8, containLabel: true },
  xAxis: { type: 'value', show: false },
  yAxis: { type: 'category', data: rank.value.map((a: any) => a.name), axisLabel: { color: '#f0e6d2', fontSize: 11 }, axisLine: { show: false }, axisTick: { show: false } },
  series: [{
    type: 'bar', barWidth: 12,
    data: rank.value.map((a: any) => Number(a.value || 0)),
    label: { show: true, position: 'right', color: '#e8c878', fontSize: 11, formatter: (p: any) => fmt(Number(p.value || 0)) },
    itemStyle: { borderRadius: [0, 6, 6, 0], color: { type: 'linear', x: 0, y: 0, x2: 1, y2: 0, colorStops: [{ offset: 0, color: '#a88a50' }, { offset: 1, color: '#e8c878' }] } },
  }],
}))
</script>

<template>
  <ScreenPanel title="红娘业绩 TOP5">
    <div style="height: 150px"><v-chart :option="agentOption" /></div>
  </ScreenPanel>
</template>
