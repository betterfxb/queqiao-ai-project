<script setup lang="ts">
/** 活动转化漏斗 — 数据源：screen.funnel */
import { computed, inject, ref } from 'vue'
import ScreenPanel from './ScreenPanel.vue'

const STAGE_COLORS = ['#e8c878', '#c9a96e', '#9a3349', '#7a2135', '#5c1828']

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
    label: { color: '#f0e6d2', fontSize: 11, formatter: '{b} {c}' },
    itemStyle: { borderColor: '#1a0810', borderWidth: 2 },
    data: funnelData.value,
  }],
}))
</script>

<template>
  <ScreenPanel title="活动转化漏斗">
    <div style="height: 180px"><v-chart :option="funnelOption" /></div>
  </ScreenPanel>
</template>
