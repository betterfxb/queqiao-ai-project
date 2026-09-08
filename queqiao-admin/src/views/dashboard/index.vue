<script setup lang="ts">
/**
 * 牵线运营中心 · Dashboard
 * 页面装配：左主列（KPI 卡片行 + 最新注册会员 + 牵线看板）+ 右侧 AI 智能助手侧栏。
 * 数据全部来自后端 /api/dashboard 聚合接口，无本地假数据。
 */
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import type { Member } from '@/api'
import { fetchDashboard } from '@/api'
import type { Kpi, KanbanColumn, MatchRec, TodoItem } from './types'
import KpiCards from './components/KpiCards.vue'
import RecentMembers from './components/RecentMembers.vue'
import MatchKanban from './components/MatchKanban.vue'
import AiAssistantPanel from './components/AiAssistantPanel.vue'

const router = useRouter()

const loading = ref(false)
const kpis = ref<Kpi[]>([])
const recentMembers = ref<Member[]>([])
const kanban = ref<KanbanColumn[]>([])
const todos = ref<TodoItem[]>([])
const recs = ref<MatchRec[]>([])

const load = async () => {
  loading.value = true
  try {
    const res: any = await fetchDashboard()
    kpis.value = res.kpis ?? []
    recentMembers.value = res.recentMembers ?? []
    kanban.value = res.kanban ?? []
    todos.value = res.todos ?? []
    recs.value = (res.recs ?? []).map((r: any) => ({
      name: r.name,
      score: r.score,
      detail: r.detail,
      reason: r.reason,
      avatarBg: r.avatarBg,
      initial: r.initial,
    }))
  } catch (e: any) {
    console.error('加载工作台失败', e)
  } finally {
    loading.value = false
  }
}

const go = (p: string) => router.push(p)
const viewMember = (m: Member) => router.push('/members/edit/' + m.id)
const openMatch = (forMemberId?: string) => {
  if (forMemberId) router.push('/match?member=' + forMemberId)
  else router.push('/match')
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="dashboard">
    <!-- 左侧主内容 -->
    <div class="main-col">
      <KpiCards :items="kpis" />

      <RecentMembers
        :members="recentMembers"
        @more="go('/members')"
        @view="viewMember"
      />

      <SectionHeader title="牵线看板" style="margin-top: 24px">
        <template #actions>
          <div class="section-more" @click="go('/match')">
            <app-icon name="plus" :size="14" />
            新建牵线
          </div>
        </template>
      </SectionHeader>
      <MatchKanban :columns="kanban" @open="go('/match')" />
    </div>

    <!-- 右侧 AI 助手 -->
    <AiAssistantPanel :todos="todos" :recs="recs" @match="openMatch" />
  </div>
</template>

<style scoped>
.dashboard { display: flex; gap: 24px; align-items: flex-start; }
.main-col { flex: 1; min-width: 0; }
</style>
