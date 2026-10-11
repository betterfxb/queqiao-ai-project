<script setup lang="ts">
/**
 * 增长中心（#111）— 5 个差异化增长引擎的统一运营入口
 *   5.3.1 转介绍激励   5.3.2 成功案例营销   5.3.3 同城运营
 *   5.3.4 产业链导流   5.3.5 渠道归因 ROI
 * 工程化拆分：本文件仅负责外壳（标题 + 标签页 + 共享上下文），
 * 各引擎逻辑分别落在 ./components 下的独立子组件中，通过 GROWTH_KEY 注入共享数据。
 */
import { ref, computed, provide, onMounted } from 'vue'
import { fetchMembers, fetchPairs } from '@/api'
import { useAuthStore } from '@/stores/auth'
import { GROWTH_KEY } from './context'
import ReferralTab from './components/ReferralTab.vue'
import StoryTab from './components/StoryTab.vue'
import CityTab from './components/CityTab.vue'
import PartnerTab from './components/PartnerTab.vue'
import ChannelTab from './components/ChannelTab.vue'

const auth = useAuthStore()
const isAdmin = computed(() => auth.role === 'ADMIN')
const isManager = computed(() => auth.role === 'MANAGER' || auth.role === 'ADMIN')
const isAgent = computed(() => auth.role === 'AGENT')

// —— 共享下拉数据源（会员 / 牵线）与通用工具，供 5 个子模块复用 ——
const members = ref<any[]>([])
const pairs = ref<any[]>([])
const memberOpts = computed(() => members.value.map((m) => ({ value: m.id, label: m.name || m.phone || m.id })))
const pairOpts = computed(() =>
  pairs.value.map((p) => ({
    value: p.id,
    label: `${p.id} · ${(p.maleName || '男')} × ${(p.femaleName || '女')}（${p.status || ''}）`,
  })),
)
const fmt = (n?: number) => (n == null ? '0.00' : Number(n).toFixed(2))
const statusType = (s: string): 'success' | 'warning' | 'info' | 'danger' =>
  s === '已发奖' || s === '已成交' || s === '已结算' || s === '已发布'
    ? 'success'
    : s === '已登记' || s === '已导流' || s === '草稿'
      ? 'info'
      : s === '已转化'
        ? 'warning'
        : 'danger'

provide(GROWTH_KEY, { isAdmin, isManager, isAgent, members, pairs, memberOpts, pairOpts, fmt, statusType })

const activeTab = ref('referral')
const loadSelectSources = async () => {
  try {
    members.value = await fetchMembers()
  } catch {
    members.value = []
  }
  try {
    pairs.value = await fetchPairs()
  } catch {
    pairs.value = []
  }
}
onMounted(loadSelectSources)
</script>

<template>
  <div class="growth-page">
    <div class="page-head">
      <div>
        <h1>增长中心</h1>
        <p>转介绍激励 · 成功案例营销 · 同城运营 · 产业链导流 · 渠道归因 ROI——让增长可运营、可衡量。</p>
      </div>
    </div>

    <el-tabs v-model="activeTab" class="tabs">
      <el-tab-pane label="转介绍激励" name="referral"><ReferralTab v-if="activeTab === 'referral'" /></el-tab-pane>
      <el-tab-pane label="成功案例营销" name="story"><StoryTab v-if="activeTab === 'story'" /></el-tab-pane>
      <el-tab-pane label="同城运营" name="city"><CityTab v-if="activeTab === 'city'" /></el-tab-pane>
      <el-tab-pane label="产业链导流" name="partner"><PartnerTab v-if="activeTab === 'partner'" /></el-tab-pane>
      <el-tab-pane label="渠道归因 ROI" name="channel"><ChannelTab v-if="activeTab === 'channel'" /></el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped>
/* 内容区沿用 AppShell 的 #content-scroll 内边距；根容器改为与财务报表/支付中心一致的全宽
   flex 纵向布局 + 统一间距，避免宽屏下被 max-width 收窄成居中窄列 */
.growth-page { display: flex; flex-direction: column; gap: 20px; }
.page-head h1 { font-size: 1.375rem; font-weight: 600; color: var(--dt-ink-1); margin: 0; }
.page-head p { margin: 6px 0 0; color: var(--dt-ink-3); font-size: 0.8125rem; }
.tabs { margin-top: 0; }
</style>
