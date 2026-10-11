<script setup lang="ts">
/** 活动转化漏斗 — 数据源：screen.funnel */
import { computed, inject, ref } from 'vue'
import ScreenPanel from './ScreenPanel.vue'

const STAGE_COLORS = ['#c3a05a', '#a8863f', '#8f3a4c', '#6b7178', '#4a4f55']

const screen = inject<any>('screen', ref(null))

const funnelData = computed<any[]>(() =>
  ((screen.value?.funnel ?? []) as any[]).map((f: any, i: number) => ({
    value: Number(f?.value || 0),
    name: String(f?.name ?? ''),
    itemStyle: { color: STAGE_COLORS[i % STAGE_COLORS.length] },
  })),
)

const funnelOption = computed(() => ({
  tooltip: { trigger: 'item' },
  series: [{
    type: 'funnel', left: 10, right: 10, top: 6, bottom: 6, minSize: '22%', gap: 4,
    label: { color: '#e8e6e3', fontSize: 11, formatter: '{b} {c}' },
    itemStyle: { borderColor: '#14161a', borderWidth: 2 },
    data: funnelData.value,
  }],
}))
</script>

<template>
  <ScreenPanel title="活动转化漏斗">
    <div style="height: 180px"><v-chart :option="funnelOption" /></div>
  </ScreenPanel>
</template>
