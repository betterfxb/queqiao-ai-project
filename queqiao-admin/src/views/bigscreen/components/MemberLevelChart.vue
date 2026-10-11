<script setup lang="ts">
/** 会员等级分布 + 付费率 — 数据源：screen.memberLevel */
import { computed, inject, ref } from 'vue'
import ScreenPanel from './ScreenPanel.vue'

const screen = inject<any>('screen', ref(null))

/** 名称含 SVIP / VIP 视为付费等级，其余归为普通 */
const clsOf = (name: string) => {
  const n = String(name ?? '').toUpperCase()
  return n.includes('SVIP') ? 'svip' : n.includes('VIP') ? 'vip' : 'normal'
}

const levels = computed<any[]>(() => {
  const list = (screen.value?.memberLevel ?? []) as any[]
  const total = list.reduce((a: number, b: any) => a + Number(b?.value || 0), 0)
  return list.map((l: any) => {
    const pct = total ? Math.round((Number(l?.value || 0) / total) * 100) : 0
    // 占比过小时保留最小宽度，保证条内文字可读
    return { name: String(l?.name ?? ''), pct, width: Math.max(pct, 20) + '%', cls: clsOf(l?.name) }
  })
})

const payRate = computed(() => {
  const list = (screen.value?.memberLevel ?? []) as any[]
  const total = list.reduce((a: number, b: any) => a + Number(b?.value || 0), 0)
  if (!total) return 0
  const paid = list.filter((l: any) => clsOf(l?.name) !== 'normal').reduce((a: number, b: any) => a + Number(b?.value || 0), 0)
  return Math.round((paid / total) * 100)
})
</script>

<template>
  <ScreenPanel title="会员等级分布">
    <div class="level-row">
      <div class="level-stack">
        <div v-for="l in levels" :key="l.name" class="lv" :class="l.cls" :style="{ width: l.width }">{{ l.name }} {{ l.pct }}%</div>
      </div>
      <div class="pay-rate"><div class="pay-num">{{ payRate }}%</div><div class="pay-label">付费率</div></div>
    </div>
  </ScreenPanel>
</template>

<style scoped>
.level-row { display: flex; align-items: center; gap: 14px; }
.level-stack { flex: 1; display: flex; flex-direction: column; gap: 4px; }
.lv { height: 26px; border-radius: var(--dt-radius-md); font-size: 0.6875rem; color: #fff; display: flex; align-items: center; padding-left: 10px; font-weight: 600; font-variant-numeric: tabular-nums; }
.lv.normal { background: #6b7178; }
.lv.vip { background: var(--bs-gold); color: #14161a; }
.lv.svip { background: var(--bs-burgundy); }
.pay-rate { text-align: center; }
.pay-num { font-size: 1.75rem; font-weight: 700; color: var(--bs-gold); font-family: var(--dt-font-num); font-variant-numeric: tabular-nums; }
.pay-label { font-size: 0.6875rem; color: #9aa1a9; }
</style>
