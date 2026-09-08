<script setup lang="ts">
/** Dashboard · KPI 指标卡片行（样式全部来自全局 .kpi-row / .kpi-card） */
import type { Kpi } from '../types'

defineProps<{ items: Kpi[] }>()
</script>

<template>
  <div class="kpi-row">
    <KpiCard
      v-for="k in items"
      :key="k.label"
      :label="k.label"
      :value="k.value"
      :unit="k.unit"
      :trend="k.trend"
      :up="k.up"
      :icon="k.icon"
      :tone="k.tone"
    />
  </div>
</template>
