<script setup lang="ts">
/**
 * CRM 客户跟进 — KPI(真实统计) + 筛选 + 六列看板(拖拽调用 /stage) + 跟进记录(调用 /follows) + AI 下一步动作 + 转化漏斗
 */
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  fetchCrmBoard,
  fetchCrmStats,
  moveLeadStage,
  addLeadFollow,
  nextActionLead,
  type CrmCard,
} from '@/api'
import CrmColumn from './components/CrmColumn.vue'

const stage = ref('全部'); const agent = ref('全部'); const nextFollow = ref('全部'); const level = ref('全部'); const search = ref('')

const loading = ref(false)
const columns = ref<{ key: string; title: string; count: number; cards: CrmCard[] }[]>([])
const stats = ref<{ total: number; deal: number; convRate: number; risk: number; star: number; byStage: Record<string, number> }>({ total: 0, deal: 0, convRate: 0, risk: 0, star: 0, byStage: {} })
const selected = ref<CrmCard | null>(null)

const load = async () => {
  loading.value = true
  try {
    const [b, s] = await Promise.all([fetchCrmBoard(), fetchCrmStats()])
    columns.value = b
    stats.value = s
    if (!selected.value) {
      const first = b.find((c) => c.cards.length)?.cards[0]
      if (first) selected.value = first
    }
  } catch (e: any) {
    ElMessage.error('加载 CRM 看板失败：' + (e?.message ?? e))
  } finally {
    loading.value = false
  }
}

const kpis = computed(() => [
  { label: '高价值客户', value: String(stats.value.star), unit: '人', trend: 'VIP 优先跟进', tone: 'gold', up: true },
  { label: '待跟进会员', value: String(Math.max(0, stats.value.total - stats.value.deal)), unit: '人', trend: '需紧急处理', tone: 'burgundy', up: false },
  { label: '流失风险', value: String(stats.value.risk), unit: '人', trend: '超过7天未跟进', tone: 'burgundy', up: false },
  { label: '跟进转化率', value: String(stats.value.convRate), unit: '%', trend: '达标', tone: 'success', up: true },
])

/** 客户端筛选（按姓名/手机/等级） */
const filteredColumns = computed(() =>
  columns.value.map((c) => ({
    ...c,
    cards: c.cards.filter((card) => {
      if (level.value !== '全部' && card.level !== level.value) return false
      if (search.value && !(card.name + (card.phone || '')).includes(search.value)) return false
      return true
    }),
  }))
)

const selectedStage = computed(() => {
  if (!selected.value) return ''
  const col = columns.value.find((c) => c.cards.some((x) => x.id === selected.value!.id))
  return col?.title ?? ''
})
const stageIndex = computed(() => {
  const titles = columns.value.map((c) => c.title)
  const i = titles.indexOf(selectedStage.value)
  return i < 0 ? 0 : i
})

/* 拖拽 */
const dragId = ref<string | null>(null)
const onDragStart = (id: string) => { dragId.value = id }
const onDrop = async (colKey: string) => {
  if (!dragId.value) return
  const col = columns.value.find((c) => c.key === colKey)
  if (!col) return
  const id = dragId.value
  dragId.value = null
  try {
    await moveLeadStage(id, col.title)
    ElMessage.success(`已将会员移至「${col.title}」阶段`)
    await load()
    const moved = columns.value.find((c) => c.cards.some((x) => x.id === id))?.cards.find((x) => x.id === id)
    if (moved) selected.value = moved
  } catch (e: any) {
    ElMessage.error('阶段变更失败：' + (e?.message ?? e))
  }
}

/* 记录跟进弹窗 */
const recordVisible = ref(false)
const recForm = ref({ method: '电话回访', content: '', nextTime: '2026-08-09 10:00' })
const saveRecord = async () => {
  if (!selected.value) return
  if (!recForm.value.content.trim()) { ElMessage.warning('请填写跟进内容'); return }
  try {
    await addLeadFollow(selected.value.id, recForm.value.content.trim(), recForm.value.method)
    ElMessage.success('跟进记录已保存')
    recordVisible.value = false
    recForm.value.content = ''
    await load()
  } catch (e: any) {
    ElMessage.error('保存失败：' + (e?.message ?? e))
  }
}

/* AI 下一步动作 */
const aiLoading = ref(false)
const aiResult = ref<{ source: string; action: string; reason: string; priority: string } | null>(null)
const askAi = async () => {
  if (!selected.value) return
  aiLoading.value = true
  try {
    aiResult.value = await nextActionLead(selected.value.id)
    if (aiResult.value) aiResult.value.action = '[AI] ' + aiResult.value.action
  } catch (e: any) {
    ElMessage.error('AI 建议失败：' + (e?.message ?? e))
  } finally {
    aiLoading.value = false
  }
}

const funnelOption = computed(() => {
  const stages = columns.value.map((c) => ({ name: c.title, value: c.cards.length }))
  const colors = ['#9a3349', '#b25a6d', '#c9a96e', '#d9bf8a', '#e8d5ac', '#4a8b5c']
  return {
    tooltip: { trigger: 'item' },
    series: [{
      type: 'funnel', left: 10, right: 10, top: 6, bottom: 6, minSize: '18%',
      label: { color: '#4a3640', fontSize: 11, formatter: '{b} {c}' },
      itemStyle: { borderColor: '#fff', borderWidth: 2 },
      data: stages.map((s, i) => ({ value: s.value, name: s.name, itemStyle: { color: colors[i % colors.length] } })),
    }],
  }
})

onMounted(load)
</script>

<template>
  <div class="crm-page">
    <div class="kpi-row">
      <div v-for="k in kpis" :key="k.label" class="kpi-card">
        <div class="kpi-info">
          <div class="kpi-label">{{ k.label }}</div>
          <div class="kpi-value">{{ k.value }}<span class="unit">{{ k.unit }}</span></div>
          <div class="kpi-trend" :class="k.up ? 'up' : 'down'">{{ k.trend }}</div>
        </div>
        <div class="kpi-icon" :class="k.tone"><app-icon name="target" :size="22" /></div>
      </div>
    </div>

    <div class="filter-bar glass-card">
      <div class="search-mini"><app-icon name="search" :size="14" /><input v-model="search" placeholder="搜索会员姓名/手机号" /></div>
      <el-select v-model="stage" size="small" style="width:110px"><el-option label="全部阶段" value="全部" /><el-option v-for="c in columns" :key="c.key" :label="c.title" :value="c.title" /></el-select>
      <el-select v-model="agent" size="small" style="width:110px"><el-option label="负责红娘" value="全部" /><el-option label="王淑芬" value="王淑芬" /></el-select>
      <el-select v-model="nextFollow" size="small" style="width:110px"><el-option label="下次跟进" value="全部" /><el-option label="今日" value="今日" /></el-select>
      <el-select v-model="level" size="small" style="width:110px"><el-option label="会员等级" value="全部" /><el-option label="VIP" value="VIP" /><el-option label="SVIP" value="SVIP" /></el-select>
      <div style="flex:1" />
      <button class="btn btn-primary btn-sm" @click="load"><app-icon name="refresh" :size="14" />刷新</button>
    </div>

    <div class="crm-body">
      <div class="kanban-wrap">
        <CrmColumn
          v-for="col in filteredColumns" :key="col.key"
          :column="col"
          :selected-id="selected?.id"
          @drop="onDrop"
          @dragstart="onDragStart"
          @select="selected = $event"
        />
      </div>

      <aside class="crm-side">
        <div class="glass-card side-card" v-if="selected">
          <div class="crm-detail-head">
            <div class="crm-avatar big" :data-gender="selected.gender">{{ selected.name[0] }}</div>
            <div>
              <div class="crm-detail-name">{{ selected.name }} <LevelBadge :level="selected.level" /></div>
              <div class="crm-detail-sub">{{ selected.phone || '—' }}</div>
            </div>
          </div>
          <div class="crm-detail-row"><span>当前阶段</span><b>{{ selectedStage || '—' }}</b></div>
          <div class="stage-progress"><div class="stage-fill" :style="{ width: ((stageIndex + 1) / 6) * 100 + '%' }" /></div>
          <div class="stage-text">阶段进度 {{ stageIndex + 1 }}/6（线索→沟通→分析→匹配→约见→成交）</div>
          <div class="crm-detail-row"><span>下次动作</span><b>{{ selected.nextAction }}</b></div>

          <div class="crm-remind" v-if="selected.fresh === 'danger'"><app-icon name="bell" :size="13" />该客户已停留 {{ selected.stayDays }} 天，流失风险较高</div>

          <div class="crm-side-actions">
            <button class="btn btn-gold btn-sm" style="width:100%" @click="recordVisible = true">记录跟进</button>
            <button class="btn btn-outline btn-sm" style="width:100%" @click="askAi" :disabled="aiLoading">
              <app-icon name="sparkles" :size="14" />{{ aiLoading ? '分析中…' : 'AI 下一步动作' }}
            </button>
          </div>

          <div v-if="aiResult" class="ai-next">
            <div class="ai-next-head">AI 建议（{{ aiResult.priority }}优先级）</div>
            <div class="ai-next-action">{{ aiResult.action }}</div>
            <div class="ai-next-reason">{{ aiResult.reason }}</div>
          </div>

          <el-input type="textarea" :rows="3" placeholder="添加跟进备注..." />
        </div>
        <div v-else class="glass-card side-card crm-empty">点击左侧卡片查看客户详情</div>
      </aside>
    </div>

    <div class="glass-card funnel-card">
      <div class="section-title"><span class="section-title-bar" />客户转化漏斗 <span class="text-muted" style="font-size:0.75rem;font-weight:400">（总转化率 {{ stats.convRate }}%）</span></div>
      <div style="height: 220px; margin-top: 8px"><v-chart :option="funnelOption" /></div>
    </div>

    <el-dialog v-model="recordVisible" title="记录跟进" width="480px">
      <div class="rec-form">
        <label>跟进方式</label>
        <el-select v-model="recForm.method" style="width:100%">
          <el-option v-for="m in ['电话回访','微信沟通','线下面谈','约见安排','资料发送']" :key="m" :label="m" :value="m" />
        </el-select>
        <label>跟进内容</label>
        <el-input v-model="recForm.content" type="textarea" :rows="4" placeholder="记录本次跟进的沟通要点..." />
        <label>下次跟进时间</label>
        <el-input v-model="recForm.nextTime" />
      </div>
      <template #footer>
        <button class="btn btn-ghost" @click="recordVisible = false">取消</button>
        <button class="btn btn-gold" @click="saveRecord">保存记录</button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.crm-page { display: flex; flex-direction: column; gap: 20px; }
.filter-bar { display: flex; align-items: center; gap: 10px; padding: 12px 18px; flex-wrap: wrap; }
.search-mini { display: flex; align-items: center; gap: 6px; height: 32px; padding: 0 10px; border-radius: 999px; background: var(--dt-glass-light); border: 1px solid var(--dt-border); color: var(--dt-ink-3); }
.search-mini input { border: none; outline: none; background: transparent; font-size: 0.75rem; width: 160px; font-family: var(--dt-font); }

.crm-body { display: flex; gap: 20px; align-items: flex-start; }
.kanban-wrap { flex: 1; min-width: 0; display: grid; grid-template-columns: repeat(3, 1fr); gap: 14px; }

.crm-side { width: 320px; flex-shrink: 0; }
.side-card { padding: 18px; display: flex; flex-direction: column; gap: 12px; }
.crm-detail-head { display: flex; gap: 12px; align-items: center; }
.crm-avatar { width: 34px; height: 34px; border-radius: 50%; color: #fff; font-weight: 700; font-size: 0.875rem; display: flex; align-items: center; justify-content: center; flex-shrink: 0; background: linear-gradient(135deg,#b8c4d0,#5a7a9a); }
.crm-avatar[data-gender='女'] { background: linear-gradient(135deg,#f4c6d0,#a8667d); }
.crm-avatar.big { width: 52px; height: 52px; font-size: 1.25rem; }
.crm-detail-name { font-size: 1.0625rem; font-weight: 800; color: var(--dt-ink-1); display: flex; align-items: center; gap: 8px; }
.crm-detail-sub { font-size: 0.75rem; color: var(--dt-ink-3); }
.crm-detail-row { display: flex; justify-content: space-between; font-size: 0.8125rem; color: var(--dt-ink-3); }
.crm-detail-row b { color: var(--dt-ink-1); }
.stage-progress { height: 8px; border-radius: 999px; background: var(--dt-muted); overflow: hidden; }
.stage-fill { height: 100%; border-radius: 999px; background: var(--dt-gold-gradient); }
.stage-text { font-size: 0.6875rem; color: var(--dt-ink-4); }
.crm-remind { font-size: 0.75rem; color: var(--dt-warning); background: var(--dt-warning-soft); border-radius: 8px; padding: 8px 10px; display: flex; align-items: center; gap: 6px; }
.crm-side-actions { display: flex; flex-direction: column; gap: 8px; }

.ai-next { background: var(--dt-glass-dark); border: 1px solid var(--dt-glass-dark-border); border-radius: 10px; padding: 12px; color: #f0e6d2; }
.ai-next-head { font-size: 0.75rem; font-weight: 700; color: var(--dt-gold-light); margin-bottom: 6px; }
.ai-next-action { font-size: 0.8125rem; font-weight: 600; line-height: 1.5; margin-bottom: 4px; }
.ai-next-reason { font-size: 0.75rem; color: rgba(240,230,210,0.7); line-height: 1.5; }
.crm-empty { color: var(--dt-ink-3); text-align: center; }

.funnel-card { padding: 20px; }
.rec-form label { display: block; font-size: 0.8125rem; font-weight: 600; color: var(--dt-ink-2); margin: 12px 0 6px; }
.rec-form label:first-child { margin-top: 0; }
</style>
