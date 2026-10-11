<script setup lang="ts">
/**
 * 会员管理 — 页面装配：搜索 + 筛选 chips + 数据表格 + 右侧高级筛选 + 快速查看抽屉 + 分页
 * 全部数据来自后端 /api/members（服务端分页 + 服务端筛选），无任何本地假数据。
 */
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  fetchMemberPage,
  fetchAgents,
  setMemberStatus,
  assignMemberAgent,
  deleteMember,
  exportMembers,
  createConversation,
  type Member,
  type MemberQuery,
} from '@/api'
import MemberFilterBar from './components/MemberFilterBar.vue'
import MemberTable from './components/MemberTable.vue'
import AdvancedFilterPanel from './components/AdvancedFilterPanel.vue'
import MemberDetailDrawer from './components/MemberDetailDrawer.vue'
import MemberInfoCard from './components/MemberInfoCard.vue'
import type { AdvancedFilter, ChipGroup, MemberView } from './types'

const router = useRouter()

const search = ref('')
const view = ref<MemberView>('list')
const sort = ref('最近活跃')

/** 排序标签 → 后端 sortBy 字段 */
const SORT_MAP: Record<string, { sortBy: string; order: 'asc' | 'desc' }> = {
  最近活跃: { sortBy: 'updatedAt', order: 'desc' },
  最新注册: { sortBy: 'createdAt', order: 'desc' },
  牵线次数: { sortBy: 'matchCount', order: 'desc' },
}

/** 年龄段标签 → { ageMin, ageMax } */
const AGE_MAP: Record<string, { ageMin?: number; ageMax?: number }> = {
  全部: {},
  '22-28': { ageMin: 22, ageMax: 28 },
  '29-35': { ageMin: 29, ageMax: 35 },
  '36-42': { ageMin: 36, ageMax: 42 },
  '43+': { ageMin: 43 },
}

/* 筛选 chips（每组单选），红娘选项运行时由后端填充 */
const chipGroups = ref<ChipGroup[]>([
  { key: 'gender', label: '性别', options: ['全部', '男', '女'], active: '全部' },
  { key: 'age', label: '年龄', options: ['全部', '22-28', '29-35', '36-42', '43+'], active: '全部' },
  { key: 'level', label: '等级', options: ['全部', '普通', 'VIP', 'SVIP', '金卡', '钻石VIP'], active: '全部' },
  { key: 'status', label: '状态', options: ['全部', '待审核', '认证中', '已认证', '牵线中', '已约见', '已暂停'], active: '全部' },
  { key: 'agent', label: '红娘', options: ['全部'], active: '全部' },
])
const chipOf = (key: string) => chipGroups.value.find((g) => g.key === key)?.active ?? '全部'

/** 高级筛选条件（未应用时为 null） */
const adv = ref<AdvancedFilter | null>(null)

const list = ref<Member[]>([])
const total = ref(0)
const loading = ref(false)
const page = ref(1)
const pageSize = ref(20)

const agentNames = ref<string[]>([])

/** 组装后端查询参数 */
const buildQuery = (): MemberQuery => {
  const s = SORT_MAP[sort.value] ?? SORT_MAP['最近活跃']
  const q: MemberQuery = {
    keyword: search.value.trim() || undefined,
    gender: chipOf('gender'),
    level: chipOf('level'),
    status: chipOf('status'),
    agent: chipOf('agent'),
    ...AGE_MAP[chipOf('age')],
    page: page.value,
    size: pageSize.value,
    sortBy: s.sortBy,
    order: s.order,
  }
  if (adv.value) {
    const [hMin, hMax] = adv.value.height
    if (hMin > 150) q.heightMin = hMin
    if (hMax < 195) q.heightMax = hMax
    q.income = adv.value.income === '不限' ? undefined : adv.value.income
    q.marriage = adv.value.marriage === '不限' ? undefined : adv.value.marriage
    q.house = adv.value.house === '不限' ? undefined : adv.value.house
    q.car = adv.value.car === '不限' ? undefined : adv.value.car
  }
  return q
}

const load = async () => {
  loading.value = true
  try {
    const r = await fetchMemberPage(buildQuery())
    list.value = r.list
    total.value = r.total
  } catch (e: any) {
    ElMessage.error(e?.message || '会员列表加载失败')
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

/** 搜索防抖 */
let timer: ReturnType<typeof setTimeout> | null = null
watch(search, () => {
  if (timer) clearTimeout(timer)
  timer = setTimeout(() => { page.value = 1; load() }, 350)
})

/** chips / 排序变化 → 回到第一页重新查询 */
watch(
  () => chipGroups.value.map((g) => g.active).join('|') + '#' + sort.value,
  () => { page.value = 1; load() },
)
watch([page, pageSize], () => load())

onMounted(async () => {
  load()
  try {
    const agents = await fetchAgents()
    agentNames.value = agents.map((a: any) => a.name).filter(Boolean)
    const g = chipGroups.value.find((x) => x.key === 'agent')
    if (g) g.options = ['全部', ...agentNames.value]
  } catch { /* 红娘筛选保持仅「全部」 */ }
})

/* 快速查看抽屉 */
const drawer = ref(false)
const current = ref<Member | null>(null)
const openQuick = (m: Member) => { current.value = m; drawer.value = true }

/* 会员信息卡片对话框 */
const cardVisible = ref(false)
const cardId = ref('')
const openCard = (m: Member) => { cardId.value = m.id; cardVisible.value = true }

/* 高级筛选 */
const applyAdv = (v: AdvancedFilter) => {
  adv.value = v
  page.value = 1
  load()
  ElMessage.success('已应用高级筛选')
}
const resetAdv = () => {
  adv.value = null
  page.value = 1
  load()
  ElMessage.info('已重置高级筛选')
}

/* 行操作 */
const newMember = () => router.push('/members/edit')
const goEdit = (row?: Member) => router.push(row?.id ? `/members/edit?id=${row.id}` : '/members/edit')
const goMatch = (row?: Member) => router.push(row?.id ? `/match?member=${row.id}` : '/match')

const busy = reactive<Record<string, boolean>>({})

const onCommand = async ({ cmd, row }: { cmd: string; row: Member }) => {
  if (busy[row.id]) return
  busy[row.id] = true
  try {
    if (cmd === 'verify') {
      await setMemberStatus(row.id, '已认证')
      ElMessage.success(`${row.name} 已通过认证`)
    } else if (cmd === 'suspend') {
      await setMemberStatus(row.id, '已暂停')
      ElMessage.success(`${row.name} 已暂停`)
    } else if (cmd === 'resume') {
      await setMemberStatus(row.id, '已认证')
      ElMessage.success(`${row.name} 已恢复`)
    } else if (cmd === 'assign') {
      const { value } = await ElMessageBox.prompt(
        `将 ${row.name} 分配给哪位红娘？${agentNames.value.length ? '（可选：' + agentNames.value.join('、') + '）' : ''}`,
        '分配红娘',
        { inputValue: row.agent || '', inputPattern: /\S/, inputErrorMessage: '请输入红娘姓名' },
      )
      await assignMemberAgent(row.id, String(value).trim())
      ElMessage.success('已分配')
    } else if (cmd === 'delete') {
      await ElMessageBox.confirm(`确定删除会员「${row.name}」？该操作不可撤销。`, '删除确认', { type: 'warning' })
      await deleteMember(row.id)
      ElMessage.success('已删除')
    }
    if (cmd !== 'noop') await load()
  } catch (e: any) {
    if (e !== 'cancel' && e !== 'close') ElMessage.error(e?.message || '操作失败')
  } finally {
    busy[row.id] = false
  }
}

/* 导出 */
const exporting = ref(false)
const doExport = async () => {
  exporting.value = true
  try {
    await exportMembers({
      keyword: search.value.trim() || undefined,
      gender: chipOf('gender'),
      level: chipOf('level'),
      status: chipOf('status'),
    })
    ElMessage.success('导出已开始')
  } catch (e: any) {
    ElMessage.error(e?.message || '导出失败')
  } finally {
    exporting.value = false
  }
}

/* 抽屉内「发送消息」→ 真实创建会话并跳转 */
const onSendMessage = async (m: Member) => {
  try {
    const conv: any = await createConversation(m.id)
    drawer.value = false
    router.push(conv?.id ? `/messages?id=${conv.id}` : '/messages')
  } catch (e: any) {
    ElMessage.error(e?.message || '创建会话失败')
  }
}

const summary = computed(() => `共 ${total.value} 位会员`)
</script>

<template>
  <div class="members-page">
    <MemberFilterBar
      v-model:search="search"
      v-model:view="view"
      v-model:sort="sort"
      :groups="chipGroups"
      :exporting="exporting"
      @new-member="newMember"
      @export="doExport"
      @refresh="load"
    />

    <div class="content-row">
      <MemberTable
        :data="list"
        :total="total"
        :loading="loading"
        v-model:page="page"
        v-model:page-size="pageSize"
        @row-click="openQuick"
        @view="openQuick"
        @edit="goEdit"
        @match="goMatch"
        @card="openCard"
        @command="onCommand"
      />

      <AdvancedFilterPanel @apply="applyAdv" @reset="resetAdv" />
    </div>

    <MemberDetailDrawer
      v-model="drawer"
      :member="current"
      @match="goMatch"
      @message="onSendMessage"
      @card="openCard"
    />

    <MemberInfoCard v-model="cardVisible" :member-id="cardId" />

    <span class="sr-only">{{ summary }}</span>
  </div>
</template>

<style scoped>
.members-page { display: flex; flex-direction: column; gap: 16px; }
.content-row { display: flex; gap: 20px; align-items: flex-start; }
.sr-only { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); }
</style>
