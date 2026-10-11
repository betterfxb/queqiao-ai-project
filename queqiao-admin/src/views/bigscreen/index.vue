<script setup lang="ts">
/**
 * 数据大屏 — 独立全屏暗色指挥中心（无 shell）
 * 页面壳负责：暗色背景 / 三栏栅格 / 返回与全屏控制，以及一次性拉取聚合数据
 * GET /api/screen，通过 provide('screen') 下发给各面板，面板自行 inject 取用。
 */
import { computed, onMounted, provide, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '@/api'
import ScreenHeader from './components/ScreenHeader.vue'
import StatTiles from './components/StatTiles.vue'
import GenderAgeChart from './components/GenderAgeChart.vue'
import AgentRankChart from './components/AgentRankChart.vue'
import ActivityFeed from './components/ActivityFeed.vue'
import RegionChart from './components/RegionChart.vue'
import MatchTrendChart from './components/MatchTrendChart.vue'
import MemberLevelChart from './components/MemberLevelChart.vue'
import FunnelChart from './components/FunnelChart.vue'
import SatisfactionGauge from './components/SatisfactionGauge.vue'
import HotTagCloud from './components/HotTagCloud.vue'

const router = useRouter()

/** 大屏聚合数据；未加载完成时为 null，各面板需自行做空值兜底 */
const screen = ref<any>(null)
provide('screen', screen)

onMounted(async () => {
  try {
    screen.value = await api<any>('/screen')
  } catch (e) {
    console.error('[bigscreen] 加载 /screen 失败', e)
  }
})

/** 按关键字在 statTiles 中查找指标 */
const findTile = (kw: string): any =>
  ((screen.value?.statTiles ?? []) as any[]).find((t: any) => String(t?.label ?? '').includes(kw))

/**
 * 底部三格。聚合接口没有「按天」维度，牵线 / 约见只能复用 statTiles 中同义指标；
 * 找不到就显示占位符，不编造具体数字。
 */
const bottomBar = computed(() => {
  const withUnit = (t: any) => (t.unit ? `${t.label}(${t.unit})` : t.label)
  const link = findTile('牵线')
  const meet = findTile('约见') ?? findTile('见面')
  const member = findTile('会员总数')
  return [
    link ? { label: withUnit(link), value: link.value } : { label: '今日牵线(对)', value: '—' },
    meet ? { label: withUnit(meet), value: meet.value } : { label: '今日约见(场)', value: '—' },
    member ? { label: '新增会员', value: member.sub || member.value } : { label: '今日新增会员(人)', value: '—' },
  ]
})

const goBack = () => router.push('/')
const toggleFull = () => { if (document.fullscreenElement) document.exitFullscreen(); else document.documentElement.requestFullscreen() }
</script>

<template>
  <div class="big-screen">
    <!-- 头部 -->
    <ScreenHeader @back="goBack" />

    <div class="bs-body">
      <!-- 左列 -->
      <div class="bs-col">
        <StatTiles />
        <GenderAgeChart />
        <AgentRankChart />
        <ActivityFeed />
      </div>

      <!-- 中列 -->
      <div class="bs-col mid">
        <RegionChart />
        <MatchTrendChart />
        <div class="bs-bottom-bar">
          <div v-for="b in bottomBar" :key="b.label" class="bb-item"><b>{{ b.value }}</b>{{ b.label }}</div>
        </div>
      </div>

      <!-- 右列 -->
      <div class="bs-col">
        <MemberLevelChart />
        <FunnelChart />
        <SatisfactionGauge />
        <HotTagCloud />
      </div>
    </div>

    <button class="full-btn" @click="toggleFull" title="全屏 (F)">⛶</button>
  </div>
</template>

<style scoped>
.big-screen {
  position: relative; height: 100vh; overflow: hidden; padding: 18px 22px;
  background: var(--bs-bg-2);
  color: var(--bs-text); font-family: var(--dt-font);
}

.bs-body { position: relative; z-index: 2; display: grid; grid-template-columns: 1fr 1.6fr 1fr; gap: 18px; height: calc(100% - 84px); }
.bs-col { display: flex; flex-direction: column; gap: 14px; min-width: 0; }

.bs-bottom-bar { display: flex; gap: 12px; }
.bb-item { flex: 1; text-align: center; background: var(--bs-panel); border: 1px solid var(--bs-border); border-radius: var(--dt-radius-lg); padding: 10px; font-size: 0.6875rem; color: #9aa1a9; }
.bb-item b { display: block; font-size: 1.375rem; color: var(--bs-gold); font-family: var(--dt-font-num); font-variant-numeric: tabular-nums; }

.full-btn { position: fixed; right: 22px; bottom: 22px; width: 40px; height: 40px; border-radius: var(--dt-radius-lg); background: var(--bs-panel-strong); border: 1px solid var(--bs-border); color: var(--bs-gold); font-size: 1.125rem; cursor: pointer; z-index: 10; }
</style>
