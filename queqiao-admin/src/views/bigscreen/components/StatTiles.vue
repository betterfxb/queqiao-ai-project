<script setup lang="ts">
/** 核心指标磁贴 — 数据源：screen.statTiles */
import { computed, inject, ref } from 'vue'
import ScreenPanel from './ScreenPanel.vue'

const screen = inject<any>('screen', ref(null))
const core = computed<any[]>(() => (screen.value?.statTiles ?? []) as any[])
</script>

<template>
  <ScreenPanel class="core" title="核心指标">
    <div class="core-grid">
      <div v-for="c in core" :key="c.label" class="core-item">
        <div class="core-label">{{ c.label }}</div>
        <div class="core-value">{{ c.value }}<span v-if="c.unit" class="core-unit">{{ c.unit }}</span></div>
        <div v-if="c.sub" class="core-trend">{{ c.sub }}</div>
      </div>
    </div>
  </ScreenPanel>
</template>

<style scoped>
.core-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.core-item { background: var(--bs-panel); border-radius: var(--dt-radius-md); padding: 12px; }
.core-label { font-size: 0.75rem; color: #9aa1a9; }
.core-value { font-size: 1.625rem; font-weight: 700; color: var(--bs-gold); font-family: var(--dt-font-num); font-variant-numeric: tabular-nums; margin-top: 4px; }
.core-unit { font-size: 0.8125rem; font-weight: 600; margin-left: 3px; }
.core-trend { font-size: 0.6875rem; color: #6b7178; margin-top: 2px; }
</style>
