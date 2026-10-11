<script setup lang="ts">
/** 大屏顶栏：品牌 / 主标题 + 实时时钟 / 数据更新时间 / 返回 */
import { computed, inject, onBeforeUnmount, onMounted, ref } from 'vue'

const emit = defineEmits<{ (e: 'back'): void }>()

const screen = inject<any>('screen', ref(null))
const updatedAt = computed(() => screen.value?.updatedAt ?? '')

const now = ref('')
let timer: any = null
onMounted(() => {
  const tick = () => {
    const d = new Date()
    now.value = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}:${String(d.getSeconds()).padStart(2, '0')}`
  }
  tick(); timer = setInterval(tick, 1000)
})
onBeforeUnmount(() => clearInterval(timer))
</script>

<template>
  <header class="bs-header">
    <div class="bs-brand">牵线运营中心 · 数据指挥中心</div>
    <div class="bs-title-wrap">
      <div class="bs-title">婚恋服务数据实时大屏</div>
      <div class="bs-clock">{{ now }}</div>
    </div>
    <div class="bs-status">
      <span class="live-dot" />实时更新
      <span v-if="updatedAt" class="bs-latency">数据更新于 {{ updatedAt }}</span>
    </div>
    <button class="bs-back" @click="emit('back')">返回</button>
  </header>
</template>

<style scoped>
.bs-header { position: relative; display: flex; align-items: center; gap: 20px; height: 64px; z-index: 2; }
.bs-brand { font-size: 0.875rem; color: #9aa1a9; letter-spacing: 0.05em; }
.bs-title-wrap { flex: 1; text-align: center; }
.bs-title { font-size: 1.5rem; font-weight: 600; color: var(--bs-text); letter-spacing: 0.08em; }
.bs-title::before, .bs-title::after { content: ''; display: inline-block; width: 60px; height: 1px; background: var(--bs-border); vertical-align: middle; margin: 0 16px; }
.bs-clock { font-size: 0.75rem; color: #6b7178; margin-top: 2px; font-family: var(--dt-font-num); font-variant-numeric: tabular-nums; }
.bs-status { display: flex; align-items: center; gap: 12px; font-size: 0.75rem; color: #9aa1a9; }
.live-dot { width: 9px; height: 9px; border-radius: 50%; background: var(--bs-burgundy); animation: pulse 1.4s infinite; }
@keyframes pulse { 0%,100% { opacity: 1; } 50% { opacity: 0.3; } }
.bs-back { background: var(--bs-panel-strong); border: 1px solid var(--bs-border); color: var(--bs-text); border-radius: var(--dt-radius-lg); padding: 6px 14px; font-size: 0.75rem; cursor: pointer; }
</style>
