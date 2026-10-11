<script setup lang="ts">
/**
 * 财务报表 — 真实接口
 * 数据：/api/finance/stats（KPI/构成/趋势）、/api/finance/records（流水）、/api/agents（业绩排行）
 * 导出：GET /api/finance/export → 浏览器下载 CSV
 */
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  exportFinance,
  fetchAgents,
  fetchFinance,
  fetchFinanceStats,
  type Agent,
  type FinanceRecord,
} from '@/api'
import FinanceStatCards from './components/FinanceStatCards.vue'
import TransactionTable from './components/TransactionTable.vue'

interface Category { name: string; value: number; percent: number }
interface TrendPoint { month: string; value: number }
interface Stats {
  total?: number
  monthRevenue?: number
  monthCount?: number
  refund?: number
  pending?: number
  orderCount?: number
  avgOrder?: number
  categories?: Category[]
  trend?: TrendPoint[]
}

const stats = ref<Stats | null>(null)
const records = ref<FinanceRecord[]>([])
const agents = ref<Agent[]>([])
const loading = ref(false)
const exporting = ref(false)

const typeFilter = ref('')
const statusFilter = ref('')
const keyword = ref('')

const monthLabel = computed(() => {
  const d = new Date()
  return `${d.getFullYear()}年${d.getMonth() + 1}月`
})

const load = async () => {
  loading.value = true
  try {
    const [s, r, a] = await Promise.all([
      fetchFinanceStats() as Promise<Stats>,
      fetchFinance(),
      fetchAgents(),
    ])
    stats.value = s
    records.value = r
    agents.value = a
  } catch (e: any) {
    ElMessage.error('财务数据加载失败：' + (e?.message ?? '未知错误'))
  } finally {
    loading.value = false
  }
}
onMounted(load)

/* ---------- 客户端筛选 ---------- */
const filtered = computed(() =>
  records.value.filter((r: any) => {
    if (typeFilter.value && r.type !== typeFilter.value) return false
    if (statusFilter.value && r.status !== statusFilter.value) return false
    if (keyword.value) {
      const k = keyword.value.trim()
      if (!(String(r.member ?? '').includes(k) || String(r.orderNo ?? '').includes(k))) return false
    }
    return true
  }),
)
const typeOptions = computed(() => [...new Set(records.value.map((r) => r.type).filter(Boolean))])
const statusOptions = computed(() => [...new Set(records.value.map((r) => r.status).filter(Boolean))])

/* ---------- 营收趋势（真实近 6 月） ---------- */
const trendOption = computed(() => {
  const t = stats.value?.trend ?? []
  return {
    tooltip: { trigger: 'axis', valueFormatter: (v: number) => '¥' + Number(v).toLocaleString() },
    grid: { left: 10, right: 16, top: 24, bottom: 10, containLabel: true },
    xAxis: {
      type: 'category',
      data: t.map((p) => p.month),
      boundaryGap: false,
      axisLabel: { color: '#78716c', fontSize: 11 },
      axisLine: { lineStyle: { color: 'rgba(28,25,23,0.1)' } },
    },
    yAxis: {
      type: 'value',
      axisLabel: {
        color: '#78716c',
        fontSize: 11,
        formatter: (v: number) => (v >= 10000 ? (v / 10000).toFixed(1) + '万' : String(v)),
      },
      splitLine: { lineStyle: { color: 'rgba(28,25,23,0.1)' } },
    },
    series: [
      {
        name: '营收',
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 7,
        data: t.map((p) => p.value),
        lineStyle: { color: '#7d2b3d', width: 3 },
        itemStyle: { color: '#7d2b3d' },
        areaStyle: { color: 'rgba(125,43,61,0.08)' },
      },
    ],
  }
})

/* ---------- 收入构成（真实分类） ---------- */
const PIE_COLORS = ['#7d2b3d', '#a8863f', '#3f5f7d', '#78716c', '#3f7050']
const pieOption = computed(() => ({
  tooltip: { trigger: 'item', formatter: '{b}: ¥{c} ({d}%)' },
  legend: { orient: 'vertical', right: 6, top: 'center', textStyle: { color: '#44403c', fontSize: 12 } },
  series: [
    {
      type: 'pie',
      radius: ['58%', '82%'],
      center: ['38%', '50%'],
      label: { show: false },
      itemStyle: { borderColor: '#fff', borderWidth: 3, borderRadius: 4 },
      data: (stats.value?.categories ?? []).map((c, i) => ({
        value: c.value,
        name: c.name,
        itemStyle: { color: PIE_COLORS[i % PIE_COLORS.length] },
      })),
    },
  ],
}))
const totalText = computed(() => '¥' + Math.round(stats.value?.total ?? 0).toLocaleString())

/* ---------- 红娘业绩排行（真实） ---------- */
const ranking = computed(() =>
  [...agents.value].sort((a, b) => (b.monthRevenue ?? 0) - (a.monthRevenue ?? 0)).slice(0, 5),
)
const maxRevenue = computed(() => Math.max(...agents.value.map((a) => a.monthRevenue ?? 0), 1))

/* ---------- 导出 ---------- */
const doExport = async () => {
  exporting.value = true
  try {
    await exportFinance()
    ElMessage.success('财务流水 CSV 已开始下载')
  } catch (e: any) {
    ElMessage.error('导出失败：' + (e?.message ?? '未知错误'))
  } finally {
    exporting.value = false
  }
}
</script>

<template>
  <div class="finance-page" v-loading="loading">
    <div class="fin-top">
      <div class="month-pill"><app-icon name="calendar" :size="15" />{{ monthLabel }}</div>
      <el-select v-model="typeFilter" placeholder="全部类型" clearable size="default" style="width: 140px">
        <el-option v-for="t in typeOptions" :key="t" :label="t" :value="t" />
      </el-select>
      <el-select v-model="statusFilter" placeholder="全部状态" clearable size="default" style="width: 130px">
        <el-option v-for="s in statusOptions" :key="s" :label="s" :value="s" />
      </el-select>
      <el-input v-model="keyword" placeholder="会员 / 订单号" clearable style="width: 180px" />
      <div style="flex: 1" />
      <button class="btn btn-ghost" :disabled="loading" @click="load">
        <app-icon name="rotate-ccw" :size="15" />刷新
      </button>
      <button class="btn btn-gold" :disabled="exporting" @click="doExport">
        <app-icon name="download" :size="15" />{{ exporting ? '导出中…' : '导出报表' }}
      </button>
    </div>

    <finance-stat-cards :stats="stats" :loading="loading" />

    <div class="fin-grid">
      <!-- 营收趋势 -->
      <div class="glass-card chart-card trend">
        <div class="section-header">
          <div class="section-title"><span class="section-title-bar" />营收趋势（近 6 个月）</div>
        </div>
        <div style="height: 280px"><v-chart :option="trendOption" autoresize /></div>
        <div class="trend-mini">
          <span>累计 <b class="text-primary">{{ totalText }}</b></span>
          <span>本月 <b class="text-gold">¥{{ Math.round(stats?.monthRevenue ?? 0).toLocaleString() }}</b></span>
          <span>客单价 <b>¥{{ Math.round(stats?.avgOrder ?? 0).toLocaleString() }}</b></span>
        </div>
      </div>

      <!-- 收入构成 -->
      <div class="glass-card chart-card">
        <div class="section-title"><span class="section-title-bar" />收入构成</div>
        <div style="height: 240px; position: relative; margin-top: 8px">
          <v-chart :option="pieOption" autoresize />
          <div class="pie-center">
            <div class="pie-center-num">{{ totalText }}</div>
            <div class="pie-center-label">累计总收入</div>
          </div>
        </div>
      </div>
    </div>

    <div class="fin-grid2">
      <!-- 红娘业绩排行 -->
      <div class="glass-card chart-card">
        <div class="section-title" style="margin-bottom: 14px">
          <span class="section-title-bar" />红娘业绩排行（本月榜单）
        </div>
        <div v-for="(r, i) in ranking" :key="r.id" class="fin-rank">
          <span class="rank-medal" :class="{ top: i < 3 }">{{ i + 1 }}</span>
          <div class="rank-avatar" :style="{ background: r.avatarBg }">{{ (r.name || '?')[0] }}</div>
          <div class="rank-main">
            <div class="rank-top">
              <span class="rank-name">{{ r.name }}</span>
              <span class="rank-amt">¥{{ (r.monthRevenue ?? 0).toLocaleString() }}</span>
            </div>
            <div class="rank-track">
              <div class="rank-fill" :style="{ width: ((r.monthRevenue ?? 0) / maxRevenue) * 100 + '%' }" />
            </div>
            <div class="rank-sub">牵线 {{ r.monthMatch ?? 0 }}次 · 成功率 {{ r.successRate ?? 0 }}%</div>
          </div>
        </div>
        <div v-if="!ranking.length" class="empty">暂无红娘业绩数据</div>
      </div>

      <!-- 交易记录 -->
      <transaction-table :records="filtered" />
    </div>
  </div>
</template>

<style scoped>
.finance-page { display: flex; flex-direction: column; gap: 20px; }
.fin-top { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.month-pill { display: inline-flex; align-items: center; gap: 8px; padding: 8px 16px; border-radius: var(--dt-radius-md); background: var(--dt-card-solid); border: 1px solid var(--dt-border); font-weight: 600; color: var(--dt-ink-1); font-size: 0.875rem; }
.fin-grid { display: grid; grid-template-columns: 1.6fr 1fr; gap: 20px; }
.fin-grid2 { display: grid; grid-template-columns: 1fr 1.4fr; gap: 20px; }
.chart-card { padding: 20px; }
.trend-mini { display: flex; gap: 24px; margin-top: 12px; font-size: 0.75rem; color: var(--dt-ink-3); }
.trend-mini b { font-size: 0.875rem; }
.pie-center { position: absolute; left: 38%; top: 50%; transform: translate(-50%, -50%); text-align: center; pointer-events: none; }
.pie-center-num { font-size: 1.125rem; font-weight: 600; color: var(--dt-ink-1); }
.pie-center-label { font-size: 0.6875rem; color: var(--dt-ink-4); }
.fin-rank { display: flex; align-items: center; gap: 12px; padding: 8px 0; }
.rank-medal { width: 24px; text-align: center; font-size: 0.8125rem; font-weight: 600; color: var(--dt-ink-4); font-family: var(--dt-font-num); flex-shrink: 0; }
.rank-medal.top { color: var(--dt-primary); }
.rank-avatar { width: 38px; height: 38px; border-radius: 50%; color: #fff; font-weight: 600; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.rank-main { flex: 1; min-width: 0; }
.rank-top { display: flex; justify-content: space-between; font-size: 0.8125rem; font-weight: 600; color: var(--dt-ink-1); }
.rank-amt { color: var(--dt-gold-dark); }
.rank-track { height: 6px; border-radius: var(--dt-radius-sm); background: var(--dt-muted); overflow: hidden; margin: 5px 0 3px; }
.rank-fill { height: 100%; border-radius: var(--dt-radius-sm); background: var(--dt-gold); }
.rank-sub { font-size: 0.6875rem; color: var(--dt-ink-4); }
.empty { color: var(--dt-ink-3); font-size: 0.8125rem; padding: 12px 0; }

@media (max-width: 1200px) {
  .fin-grid, .fin-grid2 { grid-template-columns: 1fr; }
}
</style>
