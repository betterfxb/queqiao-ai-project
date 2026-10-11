<script setup lang="ts">
/**
 * 红娘团队管理 — KPI + 筛选 + 真实红娘卡列表 + 业绩排行 + AI 智能分配(真实) + 新建红娘
 */
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  fetchAgents,
  fetchAgentStats,
  fetchAgent,
  assignSuggest,
  createAgent,
  type Agent,
} from '@/api'
import AgentStatBar from './components/AgentStatBar.vue'
import AgentCard from './components/AgentCard.vue'

const filter = ref('全部')
const search = ref('')
const sort = ref('按业绩排序')

const loading = ref(false)
const agents = ref<Agent[]>([])
const stats = ref<any>({})

const load = async () => {
  loading.value = true
  try {
    const [a, s] = await Promise.all([fetchAgents(), fetchAgentStats()])
    agents.value = a
    stats.value = s
  } catch (e: any) {
    ElMessage.error('加载红娘数据失败：' + (e?.message ?? e))
  } finally {
    loading.value = false
  }
}

const filtered = computed(() =>
  agents.value
    .filter((a) => {
      if (filter.value !== '全部' && a.status !== filter.value) return false
      if (search.value && !(a.name + a.id).includes(search.value)) return false
      return true
    })
    .sort((x, y) => (sort.value === '按成功率' ? y.successRate - x.successRate : y.monthRevenue - x.monthRevenue))
)

const ranking = computed(() => [...agents.value].sort((a, b) => b.monthRevenue - a.monthRevenue).slice(0, 5))
const maxRevenue = computed(() => Math.max(...agents.value.map((a) => a.monthRevenue), 1))

const chartOption = computed(() => ({
  grid: { left: 10, right: 40, top: 10, bottom: 10, containLabel: true },
  xAxis: { type: 'value', axisLabel: { color: '#a8a29e', fontSize: 11 }, splitLine: { lineStyle: { color: 'rgba(28,25,23,0.08)' } } },
  yAxis: {
    type: 'category',
    data: ranking.value.map((r) => r.name).reverse(),
    axisLabel: { color: '#44403c', fontSize: 12 }, axisLine: { show: false }, axisTick: { show: false },
  },
  series: [{
    type: 'bar', barWidth: 16,
    data: ranking.value.map((r) => r.monthRevenue).reverse(),
    itemStyle: { borderRadius: [0, 4, 4, 0], color: '#a8863f' },
    label: { show: true, position: 'right', color: '#856a2e', fontSize: 11, fontWeight: 600, formatter: (p: any) => '¥' + p.value.toLocaleString() },
  }],
}))

const act = (p: { label: string; agent: Agent }) => {
  if (p.label === '分配会员') { assignVisible.value = true; window.scrollTo({ top: document.body.scrollHeight, behavior: 'smooth' }); return }
  if (p.label === '查看详情') { openDetail(p.agent); return }
  if (p.label === '业绩报表') { openReport(p.agent); return }
  ElMessage.info(p.label + '（演示）')
}

/* 红娘详情 / 业绩报表（真实接口） */
const detailVisible = ref(false)
const reportVisible = ref(false)
const detailLoading = ref(false)
const detailData = ref<{ agent: Agent; members: Record<string, unknown>[]; pairs: Record<string, unknown>[] } | null>(null)

const loadDetail = async (id: string) => {
  detailLoading.value = true
  try {
    detailData.value = await fetchAgent(id)
  } catch (e: any) {
    ElMessage.error('加载红娘详情失败：' + (e?.message ?? e))
  } finally {
    detailLoading.value = false
  }
}
const openDetail = async (a: Agent) => { await loadDetail(a.id); detailVisible.value = true }
const openReport = async (a: Agent) => { await loadDetail(a.id); reportVisible.value = true }

/** 牵线统计：成功=status==='已成功' */
const pairStats = computed(() => {
  const p = detailData.value?.pairs ?? []
  const total = p.length
  const success = p.filter((x) => x.status === '已成功').length
  const ongoing = p.filter((x) => x.status === '双方同意' || x.status === '进行中').length
  const waiting = p.filter((x) => x.status === '待确认').length
  const rate = total ? Math.round((success / total) * 100) : 0
  return { total, success, ongoing, waiting, rate }
})

/* AI 智能分配 */
const assignVisible = ref(false)
const assignForm = ref({ name: '陈思雨', level: 'VIP', city: '北京' })
const assignLoading = ref(false)
const assignResult = ref<{ source: string; agent?: { id: string; name: string; level: string }; score?: number; reasons?: string[]; candidates?: { agent: Agent; score: number; reason: string }[]; list?: { agent: Agent; score: number; reason: string }[] } | null>(null)

const runAssign = async () => {
  assignLoading.value = true
  assignResult.value = null
  try {
    const r = await assignSuggest({
      member: { name: assignForm.value.name, level: assignForm.value.level, city: assignForm.value.city },
      memberLevel: assignForm.value.level,
      memberCity: assignForm.value.city,
    })
    assignResult.value = r
  } catch (e: any) {
    ElMessage.error('AI 分配失败：' + (e?.message ?? e))
  } finally {
    assignLoading.value = false
  }
}
const confirmAssign = () => {
  if (!assignResult.value?.agent) return
  ElMessage.success(`已将 ${assignForm.value.name} 分配给 ${assignResult.value.agent.name}（${assignResult.value.agent.level}）`)
  assignVisible.value = false
}

/* 新建红娘 */
const createVisible = ref(false)
const newAgent = ref({ name: '', level: '普通红娘', status: '在职', memberCount: 0, monthMatch: 0, successRate: 60, monthRevenue: 0, rating: 4.5 })
const saveAgent = async () => {
  if (!newAgent.value.name.trim()) { ElMessage.warning('请填写红娘姓名'); return }
  try {
    await createAgent({ ...newAgent.value })
    ElMessage.success('红娘已添加')
    createVisible.value = false
    newAgent.value = { name: '', level: '普通红娘', status: '在职', memberCount: 0, monthMatch: 0, successRate: 60, monthRevenue: 0, rating: 4.5 }
    await load()
  } catch (e: any) {
    ElMessage.error('保存失败：' + (e?.message ?? e))
  }
}

onMounted(load)
</script>

<template>
  <div class="agents-page">
    <AgentStatBar />

    <div class="filter-bar glass-card">
      <div class="chip-group">
        <span v-for="f in ['全部', '在职', '休假', '离职']" :key="f" class="chip" :class="{ active: filter === f }" @click="filter = f">{{ f }}</span>
      </div>
      <div style="flex:1" />
      <div class="search-mini"><app-icon name="search" :size="14" /><input v-model="search" placeholder="搜索红娘..." /></div>
      <el-select v-model="sort" size="small" style="width: 130px"><el-option label="按业绩排序" value="按业绩排序" /><el-option label="按成功率" value="按成功率" /></el-select>
      <button class="btn btn-gold btn-sm" @click="createVisible = true"><app-icon name="plus" :size="14" />新建红娘</button>
    </div>

    <div class="agents-body">
      <div class="agent-list">
        <AgentCard v-for="a in filtered" :key="a.id" :agent="a" @action="act" />
        <div v-if="!filtered.length" class="glass-card empty">无匹配红娘</div>
      </div>

      <aside class="agents-side">
        <div class="glass-card side-card">
          <div class="section-title" style="margin-bottom:16px"><span class="section-title-bar" />本月业绩排行</div>
          <div v-for="(r, i) in ranking" :key="r.id" class="rank-item">
            <span class="rank-medal" :class="{ top: i < 3 }">{{ i + 1 }}</span>
            <div class="rank-info">
              <div class="rank-name">{{ r.name }}<span class="rank-amt">¥{{ r.monthRevenue.toLocaleString() }}</span></div>
              <div class="rank-track"><div class="rank-fill" :style="{ width: (r.monthRevenue / maxRevenue) * 100 + '%' }" /></div>
            </div>
          </div>
        </div>

        <div class="glass-card ai-assign">
          <div class="assign-head"><app-icon name="sparkles" :size="15" />AI 智能分配 <span class="assign-badge">智能推荐</span></div>
          <div class="assign-text">基于红娘当前负载、擅长领域与历史成功率，为指定会员智能推荐最合适的红娘。</div>
          <button class="btn btn-gold" style="width:100%" @click="assignVisible = true"><app-icon name="zap" :size="15" />开始智能分配</button>
        </div>
      </aside>
    </div>

    <div class="glass-card chart-card">
      <div class="section-title"><span class="section-title-bar" />红娘业绩对比 <span class="text-muted" style="font-size:0.75rem;font-weight:400">（本月业绩 · 单位：元）</span></div>
      <div style="height: 240px; margin-top: 12px"><v-chart :option="chartOption" /></div>
    </div>

    <!-- AI 分配弹窗 -->
    <el-dialog v-model="assignVisible" title="AI 智能分配" width="520px">
      <div class="assign-form">
        <label>会员姓名</label>
        <el-input v-model="assignForm.name" placeholder="待分配会员姓名" />
        <label>会员等级</label>
        <el-select v-model="assignForm.level" style="width:100%">
          <el-option v-for="l in ['普通','VIP','SVIP','钻石VIP','金卡']" :key="l" :label="l" :value="l" />
        </el-select>
        <label>意向城市</label>
        <el-input v-model="assignForm.city" placeholder="如 北京" />
        <button class="btn btn-gold" style="width:100%;margin-top:8px" :disabled="assignLoading" @click="runAssign">
          <app-icon v-if="assignLoading" name="loader" :size="14" /><span v-else>生成分配建议</span>
        </button>
      </div>
      <div v-if="assignResult" class="assign-result">
        <div class="ar-best" v-if="assignResult.agent">
          <div class="ar-best-head">推荐红娘 <b>{{ assignResult.agent.name }}</b>（{{ assignResult.agent.level }}）</div>
          <div class="ar-score">匹配度 <b>{{ Math.round((assignResult.score ?? 0)) }}</b></div>
        </div>
        <div v-if="assignResult.reasons?.length" class="ar-reasons">
          <div v-for="(r, i) in assignResult.reasons" :key="i" class="ar-reason">✓ {{ r }}</div>
        </div>
        <div v-if="assignResult.candidates?.length || assignResult.list?.length" class="ar-cands">
          <div class="ar-cands-title">候选红娘</div>
          <div v-for="(c, i) in (assignResult.candidates ?? assignResult.list ?? [])" :key="i" class="ar-cand">
            <span>{{ c.agent?.name || (c as any).agent?.name }}</span>
            <span class="ar-cand-score">评分 {{ c.score }}</span>
          </div>
        </div>
        <div class="ar-src">来源：{{ assignResult.source === 'ai' ? 'AI 模型' : '本地规则' }}</div>
      </div>
      <template #footer>
        <button class="btn btn-ghost" @click="assignVisible = false">取消</button>
        <button class="btn btn-gold" :disabled="!assignResult?.agent" @click="confirmAssign">一键分配</button>
      </template>
    </el-dialog>

    <!-- 查看详情弹窗 -->
    <el-dialog v-model="detailVisible" title="红娘详情" width="640px" v-loading="detailLoading">
      <template v-if="detailData">
        <div class="detail-head">
          <BaseAvatar :size="56" :bg="detailData.agent.avatarBg">{{ detailData.agent.name?.[0] }}</BaseAvatar>
          <div>
            <div class="detail-name">{{ detailData.agent.name }} <span class="agent-id">{{ detailData.agent.id }}</span></div>
            <div class="detail-sub">
              <span class="level-badge" :class="detailData.agent.level === '金牌红娘' ? 'vip' : detailData.agent.level === '资深红娘' ? 'premium' : 'standard'">{{ detailData.agent.level }}</span>
              <span class="detail-dot" :style="{ background: detailData.agent.status === '在职' ? 'var(--dt-success)' : 'var(--dt-warning)' }" />{{ detailData.agent.status }}
              <span class="detail-meta">评分 {{ detailData.agent.rating }}</span>
              <span class="detail-meta" v-if="detailData.agent.region">区域 {{ detailData.agent.region }}</span>
              <span class="detail-meta" v-if="detailData.agent.phone">电话 {{ detailData.agent.phone }}</span>
            </div>
          </div>
        </div>

        <div class="detail-kpis">
          <div class="dk"><b>{{ detailData.agent.memberCount }}</b><span>负责会员</span></div>
          <div class="dk"><b>{{ detailData.agent.monthMatch }}</b><span>本月牵线</span></div>
          <div class="dk"><b>{{ detailData.agent.successRate }}%</b><span>成功率</span></div>
          <div class="dk gold"><b>¥{{ (detailData.agent.monthRevenue ?? 0).toLocaleString() }}</b><span>本月业绩</span></div>
        </div>

        <div class="detail-section">
          <div class="section-title"><span class="section-title-bar" />名下会员（{{ detailData.members.length }}）</div>
          <div v-if="!detailData.members.length" class="detail-empty">暂无名下会员</div>
          <div v-else class="detail-list">
            <div v-for="(m, i) in detailData.members" :key="i" class="detail-row">
              <span class="dr-name">{{ m.name }} <em v-if="m.gender">{{ m.gender }}·{{ m.age }}</em></span>
              <span class="dr-sub">{{ m.city || '—' }} · {{ m.level || '—' }}</span>
              <span class="dr-tag" :class="{ off: m.status && m.status !== '在职' }">{{ m.status || '—' }}</span>
            </div>
          </div>
        </div>

        <div class="detail-section">
          <div class="section-title"><span class="section-title-bar" />牵线记录（{{ detailData.pairs.length }}）</div>
          <div v-if="!detailData.pairs.length" class="detail-empty">暂无牵线记录</div>
          <div v-else class="detail-list">
            <div v-for="(p, i) in detailData.pairs" :key="i" class="detail-row">
              <span class="dr-name">{{ p.maleName }} <i class="dr-x">×</i> {{ p.femaleName }}</span>
              <span class="dr-sub">匹配度 {{ p.score ?? '—' }} · {{ p.stage || '—' }}</span>
              <span class="dr-tag" :class="p.status === '已成功' ? 'ok' : p.status === '待确认' ? 'wait' : ''">{{ p.status || '—' }}</span>
            </div>
          </div>
        </div>
      </template>
      <template #footer>
        <button class="btn btn-ghost" @click="detailVisible = false">关闭</button>
      </template>
    </el-dialog>

    <!-- 业绩报表弹窗 -->
    <el-dialog v-model="reportVisible" title="红娘业绩报表" width="600px" v-loading="detailLoading">
      <template v-if="detailData">
        <div class="report-kpis">
          <div class="rk"><b>{{ detailData.agent.monthMatch }}</b><span>本月牵线</span></div>
          <div class="rk"><b>{{ pairStats.total }}</b><span>累计牵线</span></div>
          <div class="rk ok"><b>{{ pairStats.success }}</b><span>成功</span></div>
          <div class="rk warn"><b>{{ pairStats.ongoing }}</b><span>进行中</span></div>
          <div class="rk gold"><b>{{ pairStats.rate }}%</b><span>成功率</span></div>
          <div class="rk gold"><b>¥{{ (detailData.agent.monthRevenue ?? 0).toLocaleString() }}</b><span>本月业绩</span></div>
        </div>

        <div class="detail-section">
          <div class="section-title"><span class="section-title-bar" />牵线明细（{{ detailData.pairs.length }}）</div>
          <div v-if="!detailData.pairs.length" class="detail-empty">暂无牵线记录</div>
          <div v-else class="detail-list">
            <div v-for="(p, i) in detailData.pairs" :key="i" class="detail-row">
              <span class="dr-name">{{ p.maleName }} <i class="dr-x">×</i> {{ p.femaleName }}</span>
              <span class="dr-sub">匹配度 {{ p.score ?? '—' }} · {{ p.stage || '—' }}</span>
              <span class="dr-tag" :class="p.status === '已成功' ? 'ok' : p.status === '待确认' ? 'wait' : ''">{{ p.status || '—' }}</span>
            </div>
          </div>
        </div>
      </template>
      <template #footer>
        <button class="btn btn-ghost" @click="reportVisible = false">关闭</button>
      </template>
    </el-dialog>

    <!-- 新建红娘弹窗 -->
    <el-dialog v-model="createVisible" title="新建红娘" width="480px">
      <div class="assign-form">
        <label>姓名</label>
        <el-input v-model="newAgent.name" placeholder="红娘姓名" />
        <label>等级</label>
        <el-select v-model="newAgent.level" style="width:100%">
          <el-option v-for="l in ['普通红娘','资深红娘','金牌红娘']" :key="l" :label="l" :value="l" />
        </el-select>
        <label>状态</label>
        <el-select v-model="newAgent.status" style="width:100%">
          <el-option label="在职" value="在职" /><el-option label="休假" value="休假" /><el-option label="离职" value="离职" />
        </el-select>
        <label>负责会员数</label>
        <el-input-number v-model="newAgent.memberCount" :min="0" style="width:100%" />
        <label>本月业绩（元）</label>
        <el-input-number v-model="newAgent.monthRevenue" :min="0" :step="1000" style="width:100%" />
      </div>
      <template #footer>
        <button class="btn btn-ghost" @click="createVisible = false">取消</button>
        <button class="btn btn-gold" @click="saveAgent">保存</button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.agents-page { display: flex; flex-direction: column; gap: 20px; }
.filter-bar { display: flex; align-items: center; gap: 12px; padding: 12px 18px; }
.chip { font-size: 0.75rem; padding: 4px 14px; border-radius: var(--dt-radius-sm); cursor: pointer; color: var(--dt-ink-2); background: var(--dt-muted); transition: all 0.15s; }
.chip.active { background: var(--dt-primary); color: #fff; font-weight: 600; }
.chip-group { display: flex; gap: 6px; }
.search-mini { display: flex; align-items: center; gap: 6px; height: 32px; padding: 0 10px; border-radius: var(--dt-radius-md); background: var(--dt-glass-light); border: 1px solid var(--dt-border); color: var(--dt-ink-3); }
.search-mini input { border: none; outline: none; background: transparent; font-size: 0.75rem; width: 120px; font-family: var(--dt-font); }

.agents-body { display: flex; gap: 20px; align-items: flex-start; }
.agent-list { flex: 1; min-width: 0; display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
.empty { padding: 40px; text-align: center; color: var(--dt-ink-3); grid-column: 1 / -1; }

.agents-side { width: 320px; flex-shrink: 0; display: flex; flex-direction: column; gap: 20px; }
.side-card { padding: 20px; }
.rank-item { display: flex; align-items: center; gap: 12px; padding: 8px 0; }
.rank-medal { width: 24px; text-align: center; font-size: 0.8125rem; font-weight: 600; color: var(--dt-ink-4); font-family: var(--dt-font-num); flex-shrink: 0; }
.rank-medal.top { color: var(--dt-primary); }
.rank-info { flex: 1; min-width: 0; }
.rank-name { display: flex; justify-content: space-between; font-size: 0.8125rem; font-weight: 600; color: var(--dt-ink-1); margin-bottom: 4px; }
.rank-amt { color: var(--dt-gold-dark); }
.rank-track { height: 6px; border-radius: var(--dt-radius-sm); background: var(--dt-muted); overflow: hidden; }
.rank-fill { height: 100%; border-radius: var(--dt-radius-sm); background: var(--dt-primary); }

.ai-assign { padding: 20px; background: var(--dt-primary-soft); border: 1px solid var(--dt-border); color: var(--dt-ink-2); }
.assign-head { display: flex; align-items: center; gap: 8px; font-size: 0.875rem; font-weight: 600; color: var(--dt-primary); margin-bottom: 12px; }
.assign-badge { font-size: 0.625rem; background: var(--dt-gold); color: #fff; padding: 2px 8px; border-radius: var(--dt-radius-sm); margin-left: auto; }
.assign-text { font-size: 0.8125rem; line-height: 1.6; color: var(--dt-ink-3); margin-bottom: 14px; }

.chart-card { padding: 20px; }

.assign-form label { display: block; font-size: 0.8125rem; font-weight: 600; color: var(--dt-ink-2); margin: 12px 0 6px; }
.assign-form label:first-child { margin-top: 0; }
.assign-result { margin-top: 16px; border-top: 1px solid var(--dt-border); padding-top: 14px; }
.ar-best { display: flex; align-items: center; justify-content: space-between; margin-bottom: 10px; }
.ar-best-head { font-size: 0.875rem; color: var(--dt-ink-1); }
.ar-best-head b { color: var(--dt-gold-dark); }
.ar-score { font-size: 0.8125rem; color: var(--dt-ink-3); }
.ar-score b { font-size: 1.25rem; color: var(--dt-gold-dark); }
.ar-reasons { display: flex; flex-direction: column; gap: 4px; margin-bottom: 10px; }
.ar-reason { font-size: 0.75rem; color: var(--dt-success); }
.ar-cands-title { font-size: 0.75rem; font-weight: 600; color: var(--dt-ink-2); margin-bottom: 6px; }
.ar-cand { display: flex; justify-content: space-between; font-size: 0.8125rem; color: var(--dt-ink-2); padding: 4px 0; border-bottom: 1px solid var(--dt-border); }
.ar-cand-score { color: var(--dt-gold-dark); }
.ar-src { font-size: 0.6875rem; color: var(--dt-ink-4); margin-top: 10px; }

/* 详情 / 报表弹窗 */
.detail-head { display: flex; align-items: center; gap: 14px; margin-bottom: 16px; }
.detail-name { font-size: 1.125rem; font-weight: 600; color: var(--dt-ink-1); display: flex; align-items: center; gap: 8px; }
.detail-name .agent-id { font-size: 0.75rem; color: var(--dt-ink-4); font-weight: 400; }
.detail-sub { display: flex; align-items: center; gap: 10px; margin-top: 6px; flex-wrap: wrap; }
.detail-dot { width: 8px; height: 8px; border-radius: 50%; display: inline-block; }
.detail-meta { font-size: 0.75rem; color: var(--dt-ink-3); }
.detail-kpis { display: grid; grid-template-columns: repeat(4, 1fr); gap: 10px; margin-bottom: 18px; }
.dk { text-align: center; padding: 12px 6px; border-radius: var(--dt-radius-md); background: var(--dt-muted); }
.dk b { display: block; font-size: 1.125rem; font-weight: 600; color: var(--dt-ink-1); }
.dk.gold b { color: var(--dt-gold-dark); }
.dk span { font-size: 0.6875rem; color: var(--dt-ink-4); margin-top: 2px; }
.detail-section { margin-bottom: 16px; }
.detail-empty { font-size: 0.8125rem; color: var(--dt-ink-4); padding: 14px 0; text-align: center; }
.detail-list { display: flex; flex-direction: column; gap: 6px; max-height: 260px; overflow: auto; }
.detail-row { display: flex; align-items: center; gap: 12px; padding: 8px 12px; border-radius: var(--dt-radius-md); background: var(--dt-glass-light); border: 1px solid var(--dt-border); }
.dr-name { flex: 1; min-width: 0; font-size: 0.8125rem; font-weight: 600; color: var(--dt-ink-1); }
.dr-name em { font-style: normal; font-size: 0.6875rem; color: var(--dt-ink-4); margin-left: 4px; }
.dr-x { font-style: normal; color: var(--dt-gold-dark); margin: 0 4px; }
.dr-sub { font-size: 0.75rem; color: var(--dt-ink-3); white-space: nowrap; }
.dr-tag { font-size: 0.6875rem; padding: 2px 10px; border-radius: var(--dt-radius-sm); background: var(--dt-muted); color: var(--dt-ink-2); white-space: nowrap; }
.dr-tag.ok { background: var(--dt-success-soft); color: var(--dt-success); }
.dr-tag.wait { background: var(--dt-warning-soft); color: var(--dt-warning); }
.dr-tag.off { background: var(--dt-muted); color: var(--dt-ink-4); }
.report-kpis { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; margin-bottom: 18px; }
.rk { text-align: center; padding: 12px 6px; border-radius: var(--dt-radius-md); background: var(--dt-muted); }
.rk b { display: block; font-size: 1.125rem; font-weight: 600; color: var(--dt-ink-1); }
.rk.ok b { color: var(--dt-success); }
.rk.warn b { color: var(--dt-warning); }
.rk.gold b { color: var(--dt-gold-dark); }
.rk span { font-size: 0.6875rem; color: var(--dt-ink-4); margin-top: 2px; }

.ar-best-head { font-size: 0.875rem; color: var(--dt-ink-1); }
.ar-best-head b { color: var(--dt-gold-dark); }
.ar-score { font-size: 0.8125rem; color: var(--dt-ink-3); }
.ar-score b { font-size: 1.25rem; color: var(--dt-gold-dark); }
.ar-reasons { display: flex; flex-direction: column; gap: 4px; margin-bottom: 10px; }
.ar-reason { font-size: 0.75rem; color: var(--dt-success); }
.ar-cands-title { font-size: 0.75rem; font-weight: 600; color: var(--dt-ink-2); margin-bottom: 6px; }
.ar-cand { display: flex; justify-content: space-between; font-size: 0.8125rem; color: var(--dt-ink-2); padding: 4px 0; border-bottom: 1px solid var(--dt-border); }
.ar-cand-score { color: var(--dt-gold-dark); }
.ar-src { font-size: 0.6875rem; color: var(--dt-ink-4); margin-top: 10px; }
</style>
