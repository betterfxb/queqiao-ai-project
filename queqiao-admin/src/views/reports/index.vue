<script setup lang="ts">
/**
 * 报表导出 — 真实接口
 * 生成：POST /api/reports/generate → 返回记录后立即 GET /api/reports/{id}/download 下载真实 CSV
 * 记录：GET /api/reports/records   删除：DELETE /api/reports/{id}
 * 预览：直接读取对应业务列表接口的前若干行真实数据
 */
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  deleteReport,
  downloadReport,
  fetchActivities,
  fetchAgents,
  fetchFinance,
  fetchMembers,
  fetchPairs,
  fetchReports,
  fetchReportSummary,
  generateReport,
  type ReportRecord,
} from '@/api'
import { REPORT_TEMPLATES, type ReportTemplate } from './types'
import ReportTemplates from './components/ReportTemplates.vue'
import ExportPanel from './components/ExportPanel.vue'
import ReportTable from './components/ReportTable.vue'

const records = ref<ReportRecord[]>([])
const summary = ref<any>({})
const counts = ref<Record<string, number>>({})
const loading = ref(false)
const busy = ref(false)

const templates = REPORT_TEMPLATES
const selected = ref<ReportTemplate>(templates[0])
const pick = (t: ReportTemplate) => (selected.value = t)

/* ---------- 加载 ---------- */
const load = async () => {
  loading.value = true
  try {
    const [rec, sum] = await Promise.all([fetchReports(), fetchReportSummary()])
    records.value = rec
    summary.value = sum ?? {}
  } catch (e: any) {
    ElMessage.error('报表记录加载失败：' + (e?.message ?? '未知错误'))
  } finally {
    loading.value = false
  }
}

/** 各模板对应的真实数据量 */
const loadCounts = async () => {
  try {
    const [m, f, a, act, p] = await Promise.all([
      fetchMembers({ page: 1, size: 1000 }),
      fetchFinance(),
      fetchAgents(),
      fetchActivities(),
      fetchPairs(),
    ])
    counts.value = {
      会员: (m as any[]).length,
      财务: (f as any[]).length,
      红娘: (a as any[]).length,
      活动: (act as any[]).length,
      牵线: (p as any[]).length,
    }
  } catch {
    /* 统计失败不阻塞主流程 */
  }
}

onMounted(() => {
  load()
  loadCounts()
})

/* ---------- KPI（真实） ---------- */
const kpis = computed(() => {
  const now = new Date()
  const ym = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
  const monthCount = records.value.filter((r) => String(r.exportedAt ?? '').startsWith(ym)).length
  const done = records.value.filter((r) => r.status === '已完成').length
  const rate = records.value.length ? Math.round((done / records.value.length) * 100) : 100
  const latest = records.value
    .map((r) => r.exportedAt)
    .filter(Boolean)
    .sort()
    .pop()
  return [
    { label: '本月已生成报表', value: String(monthCount), unit: '份', tone: 'gold' },
    { label: '累计导出记录', value: String(records.value.length), unit: '份', tone: 'burgundy' },
    { label: '导出成功率', value: String(rate), unit: '%', tone: 'success' },
    { label: '最近导出', value: latest ? String(latest).slice(5, 16) : '—', unit: '', tone: 'gold' },
  ]
})

/* ---------- 生成 + 下载 ---------- */
const doExport = async (payload: { category: string; format: string; range: string }) => {
  busy.value = true
  try {
    const r: any = await generateReport(payload)
    ElMessage.success(`已生成《${r?.name ?? payload.category}》，开始下载`)
    await load()
    if (r?.id) await downloadReport(r.id, r.name ?? r.id)
  } catch (e: any) {
    ElMessage.error('导出失败：' + (e?.message ?? '未知错误'))
  } finally {
    busy.value = false
  }
}

const exportTemplate = (t: ReportTemplate) => {
  selected.value = t
  const now = new Date()
  doExport({
    category: t.cat,
    format: t.formats[0],
    range: `${now.getFullYear()}-01-01 ~ ${now.toISOString().slice(0, 10)}`,
  })
}

/* ---------- 记录操作 ---------- */
const onDownload = async (row: ReportRecord) => {
  try {
    await downloadReport((row as any).id, row.name)
    ElMessage.success('已开始下载')
  } catch (e: any) {
    ElMessage.error('下载失败：' + (e?.message ?? '未知错误'))
  }
}

const onRemove = async (row: ReportRecord) => {
  try {
    await ElMessageBox.confirm(`确认删除报表记录《${row.name}》？`, '删除确认', { type: 'warning' })
  } catch {
    return
  }
  try {
    await deleteReport((row as any).id)
    ElMessage.success('记录已删除')
    await load()
  } catch (e: any) {
    ElMessage.error('删除失败：' + (e?.message ?? '未知错误'))
  }
}

/* ---------- 预览（真实源数据前若干行） ---------- */
const previewVisible = ref(false)
const previewLoading = ref(false)
const previewRows = ref<Record<string, unknown>[]>([])
const previewCols = ref<{ prop: string; label: string }[]>([])

const PREVIEW_COLS: Record<string, { prop: string; label: string }[]> = {
  会员: [
    { prop: 'id', label: '编号' }, { prop: 'name', label: '姓名' }, { prop: 'gender', label: '性别' },
    { prop: 'age', label: '年龄' }, { prop: 'city', label: '城市' }, { prop: 'level', label: '等级' },
    { prop: 'agent', label: '红娘' },
  ],
  财务: [
    { prop: 'orderNo', label: '订单号' }, { prop: 'member', label: '会员' }, { prop: 'type', label: '类型' },
    { prop: 'amount', label: '金额' }, { prop: 'status', label: '状态' }, { prop: 'time', label: '时间' },
  ],
  红娘: [
    { prop: 'id', label: '编号' }, { prop: 'name', label: '姓名' }, { prop: 'level', label: '等级' },
    { prop: 'status', label: '状态' }, { prop: 'memberCount', label: '负责会员' },
    { prop: 'monthMatch', label: '本月牵线' }, { prop: 'successRate', label: '成功率' },
  ],
  活动: [
    { prop: 'id', label: '编号' }, { prop: 'title', label: '标题' }, { prop: 'date', label: '日期' },
    { prop: 'place', label: '地点' }, { prop: 'status', label: '状态' }, { prop: 'signed', label: '报名' },
  ],
  牵线: [
    { prop: 'id', label: '编号' }, { prop: 'maleName', label: '男方' }, { prop: 'femaleName', label: '女方' },
    { prop: 'score', label: '匹配度' }, { prop: 'status', label: '状态' }, { prop: 'agent', label: '红娘' },
  ],
}

const openPreview = async () => {
  previewVisible.value = true
  previewLoading.value = true
  previewRows.value = []
  previewCols.value = PREVIEW_COLS[selected.value.cat] ?? []
  try {
    const loaders: Record<string, () => Promise<any[]>> = {
      会员: () => fetchMembers({ page: 1, size: 8 }) as Promise<any[]>,
      财务: () => fetchFinance() as Promise<any[]>,
      红娘: () => fetchAgents() as Promise<any[]>,
      活动: () => fetchActivities() as Promise<any[]>,
      牵线: () => fetchPairs() as Promise<any[]>,
    }
    const rows = await (loaders[selected.value.cat] ?? loaders['会员'])()
    previewRows.value = rows.slice(0, 8)
  } catch (e: any) {
    ElMessage.error('预览加载失败：' + (e?.message ?? '未知错误'))
  } finally {
    previewLoading.value = false
  }
}
</script>

<template>
  <div class="reports-page">
    <div class="rep-top">
      <div class="sum-pill" v-if="summary?.memberTotal !== undefined">
        <app-icon name="database" :size="15" />
        数据源：会员 {{ summary.memberTotal }} · 牵线 {{ summary.pairTotal }} · 成功率 {{ summary.successRate }}%
      </div>
      <div style="flex: 1" />
      <button class="btn btn-ghost" :disabled="loading" @click="load">
        <app-icon name="rotate-ccw" :size="15" />刷新记录
      </button>
    </div>

    <div class="kpi-row">
      <div v-for="k in kpis" :key="k.label" class="kpi-card">
        <div class="kpi-info">
          <div class="kpi-label">{{ k.label }}</div>
          <div class="kpi-value">{{ k.value }}<span class="unit">{{ k.unit }}</span></div>
        </div>
        <div class="kpi-icon" :class="k.tone"><app-icon name="file-text" :size="22" /></div>
      </div>
    </div>

    <div class="rep-body">
      <report-templates
        :templates="templates"
        :selected="selected"
        :counts="counts"
        @pick="pick"
        @export="exportTemplate"
      />
      <export-panel :selected="selected" :busy="busy" @preview="openPreview" @export="doExport" />
    </div>

    <report-table :records="records" :loading="loading" @download="onDownload" @remove="onRemove" />

    <!-- 预览弹窗：展示真实源数据前 8 行 -->
    <el-dialog v-model="previewVisible" :title="`数据预览 — ${selected.name}`" width="860px">
      <el-table v-loading="previewLoading" :data="previewRows" size="small" empty-text="暂无数据">
        <el-table-column
          v-for="c in previewCols"
          :key="c.prop"
          :prop="c.prop"
          :label="c.label"
          show-overflow-tooltip
        />
      </el-table>
      <p class="preview-tip">
        仅展示前 {{ previewRows.length }} 行；导出文件包含全部
        {{ counts[selected.cat] ?? '—' }} 条记录。
      </p>
      <template #footer>
        <button class="btn btn-ghost" @click="previewVisible = false">关闭</button>
        <button
          class="btn btn-gold"
          :disabled="busy"
          @click="previewVisible = false; exportTemplate(selected)"
        >
          确认导出
        </button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.reports-page { display: flex; flex-direction: column; gap: 20px; }
.rep-top { display: flex; align-items: center; gap: 12px; }
.sum-pill {
  display: inline-flex; align-items: center; gap: 8px;
  padding: 8px 16px; border-radius: 999px;
  background: var(--dt-glass); border: 1px solid var(--dt-glass-border);
  font-size: 0.8125rem; color: var(--dt-ink-2); font-weight: 600;
}
.rep-body { display: flex; gap: 20px; align-items: flex-start; }
.preview-tip { font-size: 0.75rem; color: var(--dt-ink-3); margin: 12px 0 0; }

@media (max-width: 1200px) {
  .rep-body { flex-direction: column; }
  .rep-body :deep(.rep-config) { width: 100%; }
}
</style>
