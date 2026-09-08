<script setup lang="ts">
/** KpiCard — 仪表盘 KPI 指标卡，样式来自全局 .kpi-* */
withDefaults(
  defineProps<{
    label: string
    value: string | number
    unit?: string
    trend?: string
    up?: boolean
    icon?: string
    tone?: 'gold' | 'burgundy' | 'warning' | 'success'
  }>(),
  { up: true },
)
</script>

<template>
  <div class="kpi-card">
    <div class="kpi-info">
      <div class="kpi-label">{{ label }}</div>
      <div class="kpi-value">
        {{ value }}<span v-if="unit" class="unit">{{ unit }}</span>
      </div>
      <div v-if="trend" class="kpi-trend" :class="up ? 'up' : 'down'">
        <app-icon :name="up ? 'trending' : 'chevron-down'" :size="12" />
        {{ trend }}
      </div>
    </div>
    <div v-if="icon" class="kpi-icon" :class="tone">
      <app-icon :name="icon" :size="22" />
    </div>
  </div>
</template>
