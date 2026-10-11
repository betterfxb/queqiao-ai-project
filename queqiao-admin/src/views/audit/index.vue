<script setup lang="ts">
/**
 * 审核工作台 — KPI(真实统计) + Tabs + 审核队列(真实数据) + 右侧详情 + AI 审核建议 + 驳回原因弹窗
 */
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  fetchAudits,
  fetchAuditStats,
  approveAudit,
  rejectAudit,
  aiSuggestAudit,
  type AuditItem,
  type AuditStats,
} from '@/api'
import AuditToolbar from './components/AuditToolbar.vue'
import AuditCard from './components/AuditCard.vue'

const TABS = [
  { key: 'all', label: '全部', type: '' },
  { key: 'cert', label: '实名认证', type: '实名认证' },
  { key: 'edit', label: '资料修改', type: '资料修改' },
  { key: 'upgrade', label: '会员升级', type: '会员升级' },
  { key: 'activity', label: '活动报名', type: '活动报名' },
]

const activeTab = ref('all')
const timeRange = ref('今日')
const agentFilter = ref('全部')
const urgencyFilter = ref('全部')
const search = ref('')

const loading = ref(false)
const queue = ref<AuditItem[]>([])
const stats = ref<AuditStats>({ pending: 0, urgent: 0, approved: 0, rejected: 0, byType: {} })
const selected = ref<AuditItem | null>(null)

const tabType = computed(() => TABS.find((t) => t.key === activeTab.value)?.type ?? '')
const tabCounts = computed(() => {
  const c: Record<string, number> = { all: queue.value.length }
  for (const t of TABS) if (t.type) c[t.key] = queue.value.filter((q) => q.type === t.type).length
  return c
})

const filteredQueue = computed(() =>
  queue.value.filter((q) => {
    if (tabType.value && q.type !== tabType.value) return false
    if (urgencyFilter.value !== '全部' && q.urgency !== urgencyFilter.value) return false
    if (agentFilter.value !== '全部' && q.agent !== agentFilter.value) return false
    if (search.value && !(q.applicant + q.memberId).includes(search.value)) return false
    return true
  })
)

const kpis = computed(() => [
  { label: '待审核', value: String(stats.value.pending), unit: '条', trend: '需及时处理', tone: 'warning', up: false },
  { label: '紧急事项', value: String(stats.value.urgent), unit: '条', trend: '优先处理', tone: 'burgundy', up: false },
  { label: '今日已审', value: String(stats.value.approved + stats.value.rejected), unit: '条', trend: '+6 较昨日', tone: 'success', up: true },
  { label: '驳回率', value: stats.value.approved + stats.value.rejected ? Math.round((stats.value.rejected / (stats.value.approved + stats.value.rejected)) * 100) + '' : '0', unit: '%', trend: '-2% 较昨日', tone: 'gold', up: false },
])

const load = async () => {
  loading.value = true
  try {
    const [q, s] = await Promise.all([fetchAudits(), fetchAuditStats()])
    queue.value = q
    stats.value = s
    if (!selected.value && q.length) selected.value = q[0]
  } catch (e: any) {
    ElMessage.error('加载审核队列失败：' + (e?.message ?? e))
  } finally {
    loading.value = false
  }
}

const select = (item: AuditItem) => { selected.value = item; aiResult.value = null }

/* 驳回弹窗 */
const rejectVisible = ref(false)
const rejectTarget = ref<AuditItem | null>(null)
const reasonGroups = ref([
  { label: '证件与身份', options: ['照片模糊/遮挡', '证件过期', '证件信息不符', '身份证号校验失败', '人脸比对未通过', '非本人证件'] },
  { label: '资料完整性', options: ['资料不完整', '收入证明不全', '学历证明缺失', '房产/车产证明缺失', '婚姻状况证明缺失'] },
  { label: '内容合规', options: ['资料涉嫌造假', '照片含违规内容', '简介含敏感词', '重复提交申请'] },
  { label: '业务规则', options: ['不符合SVIP升级条件', '活动名额已满', '牵线双方条件不匹配', '会员账号异常', '其他原因'] },
])
const selectedReasons = ref<string[]>([])
const rejectNote = ref('')

const openReject = (item: AuditItem) => { rejectTarget.value = item; selectedReasons.value = []; rejectNote.value = ''; rejectVisible.value = true }
const toggleReason = (r: string) => {
  const i = selectedReasons.value.indexOf(r)
  if (i >= 0) selectedReasons.value.splice(i, 1)
  else selectedReasons.value.push(r)
}
const confirmReject = async () => {
  if (!selectedReasons.value.length) { ElMessage.warning('请选择驳回原因'); return }
  if (!rejectTarget.value) return
  const reason = selectedReasons.value.join('；') + (rejectNote.value ? '；' + rejectNote.value : '')
  try {
    await rejectAudit(rejectTarget.value.id, reason)
    ElMessage.success('已驳回并通知会员')
    rejectVisible.value = false
    await load()
  } catch (e: any) {
    ElMessage.error('驳回失败：' + (e?.message ?? e))
  }
}
const approve = async (item: AuditItem) => {
  try {
    await approveAudit(item.id)
    ElMessage.success('已通过审核')
    await load()
  } catch (e: any) {
    ElMessage.error('通过失败：' + (e?.message ?? e))
  }
}

/* AI 审核建议 */
const aiLoading = ref(false)
const aiResult = ref<{
  source: string
  decision?: string
  label?: string
  confidence?: number
  reasons?: string[]
  risks?: string[]
  advice?: string
} | null>(null)
const askAi = async () => {
  if (!selected.value) return
  aiLoading.value = true
  try {
    aiResult.value = await aiSuggestAudit(selected.value.id)
  } catch (e: any) {
    ElMessage.error('AI 建议获取失败：' + (e?.message ?? e))
  } finally {
    aiLoading.value = false
  }
}

const typeTone = (t: AuditItem['type']) =>
  t === '实名认证' ? 'primary' : t === '资料修改' ? 'info' : t === '会员升级' ? 'gold' : 'success'
const urgencyTone = (u: AuditItem['urgency']) => (u === '紧急' ? 'error' : u === '普通' ? 'warning' : 'neutral')
const statusTone = (s?: string) => (s === '已通过' ? 'success' : s === '已驳回' ? 'error' : 'warning')

onMounted(load)
</script>

<template>
  <div class="audit-page">
    <div class="kpi-row">
      <div v-for="k in kpis" :key="k.label" class="kpi-card">
        <div class="kpi-info">
          <div class="kpi-label">{{ k.label }}</div>
          <div class="kpi-value">{{ k.value }}<span class="unit">{{ k.unit }}</span></div>
          <div class="kpi-trend" :class="k.up ? 'up' : 'down'">{{ k.trend }}</div>
        </div>
        <div class="kpi-icon" :class="k.tone"><app-icon name="shield-check" :size="22" /></div>
      </div>
    </div>

    <AuditToolbar
      v-model:activeTab="activeTab"
      v-model:timeRange="timeRange"
      v-model:agentFilter="agentFilter"
      v-model:urgencyFilter="urgencyFilter"
      v-model:search="search"
    />

    <div class="audit-body">
      <div class="queue-col">
        <div v-if="loading" class="empty glass-card">加载中…</div>
        <template v-else>
          <AuditCard
            v-for="item in filteredQueue" :key="item.id"
            :item="item"
            :selected="selected?.id === item.id"
            :passed="item.status === '已通过'"
            @select="select(item)"
            @reject="openReject(item)"
            @approve="approve(item)"
          />
          <div v-if="!filteredQueue.length" class="empty glass-card">当前筛选条件下暂无审核事项</div>
        </template>
      </div>

      <aside v-if="selected" class="glass-card detail-panel">
        <div class="dp-head">
          <div class="table-avatar" style="width:52px;height:52px;font-size:1.25rem">{{ selected.applicant[0] }}</div>
          <div>
            <div class="dp-name">{{ selected.applicant }} <span class="ga-tag">{{ selected.gender }}·{{ selected.age }}</span></div>
            <div class="dp-sub">{{ selected.memberId }} · 提交红娘 {{ selected.agent }}</div>
          </div>
          <span class="type-badge" :class="typeTone(selected.type)" style="margin-left:auto">{{ selected.type }}</span>
        </div>

        <div class="dp-grid">
          <div class="dp-row"><span>紧急程度</span><span class="status-pill" :class="urgencyTone(selected.urgency)">{{ selected.urgency }}</span></div>
          <div class="dp-row"><span>当前状态</span><span class="status-pill" :class="statusTone(selected.status)">{{ selected.status || '待审核' }}</span></div>
          <div class="dp-row"><span>提交时间</span><b>{{ selected.timeAgo }}</b></div>
          <div class="dp-row"><span>审核类型</span><b>{{ selected.type }}</b></div>
        </div>

        <div v-if="selected.change" class="dp-section">变更内容</div>
        <div v-if="selected.change" class="id-row"><span>{{ selected.change.field }}</span><b><s style="color:var(--dt-ink-4)">{{ selected.change.from }}</s> → {{ selected.change.to }}</b></div>

        <div v-if="selected.pay" class="dp-section">费用信息</div>
        <div v-if="selected.pay" class="ai-item-pay" :class="{ paid: selected.pay.paid }">{{ selected.pay.text }}</div>

        <!-- AI 审核建议 -->
        <div class="ai-suggest">
          <div class="ai-suggest-head">
            <app-icon name="sparkles" :size="14" />AI 审核建议
            <button class="ai-btn" :disabled="aiLoading" @click="askAi">
              <app-icon v-if="aiLoading" name="loader" :size="12" /><span v-else>获取建议</span>
            </button>
          </div>
          <div v-if="aiResult" class="ai-body">
            <div class="ai-decision" :class="aiResult.decision">
              <b>{{ aiResult.label || aiResult.decision || '建议' }}</b>
              <span class="ai-conf">置信度 {{ Math.round((aiResult.confidence ?? 0) * 100) }}%</span>
            </div>
            <div class="conf-bar"><div class="conf-fill" :style="{ width: Math.round((aiResult.confidence ?? 0) * 100) + '%' }" /></div>
            <div v-if="aiResult.reasons?.length" class="ai-list">
              <div v-for="(r, i) in aiResult.reasons" :key="'r' + i" class="ai-li ok"><app-icon name="check" :size="12" />{{ r }}</div>
            </div>
            <div v-if="aiResult.risks?.length" class="ai-list">
              <div v-for="(r, i) in aiResult.risks" :key="'k' + i" class="ai-li warn"><app-icon name="alert" :size="12" />{{ r }}</div>
            </div>
            <div v-if="aiResult.advice" class="ai-advice">建议：{{ aiResult.advice }}</div>
            <div class="ai-src">来源：{{ aiResult.source === 'ai' ? 'AI 模型' : '本地规则' }}</div>
          </div>
          <div v-else-if="!aiLoading" class="ai-hint">点击「获取建议」由 AI 基于材料完整度、支付状态与历史记录给出审核决策</div>
        </div>

        <div class="dp-actions">
          <button class="btn btn-danger" style="flex:1" @click="openReject(selected)">驳回申请</button>
          <button class="btn btn-gold" style="flex:1" @click="approve(selected)"><app-icon name="check" :size="15" />通过审核</button>
        </div>
        <div class="dp-tip">通过后将自动通知会员并更新认证状态</div>
      </aside>
    </div>

    <el-dialog v-model="rejectVisible" title="驳回原因" width="520px" class="reject-dialog">
      <div v-for="g in reasonGroups" :key="g.label" class="reason-group">
        <div class="reason-label">{{ g.label }}</div>
        <div class="reason-chips">
          <span
            v-for="r in g.options" :key="r"
            class="chip" :class="{ active: selectedReasons.includes(r) }"
            @click="toggleReason(r)"
          >{{ r }}</span>
        </div>
      </div>
      <el-input v-model="rejectNote" type="textarea" :rows="3" placeholder="补充说明（选填）" style="margin-top: 12px" />
      <template #footer>
        <button class="btn btn-ghost" @click="rejectVisible = false">取消</button>
        <button class="btn btn-reject" @click="confirmReject">确认驳回</button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.audit-page { display: flex; flex-direction: column; gap: 20px; }
.audit-body { display: flex; gap: 20px; align-items: flex-start; }
.queue-col { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 14px; }
.empty { padding: 60px; text-align: center; color: var(--dt-ink-3); }

.detail-panel { width: 420px; flex-shrink: 0; padding: 22px; position: sticky; top: 0; }
.dp-head { display: flex; align-items: center; gap: 12px; margin-bottom: 18px; }
.type-badge { font-size: 0.75rem; font-weight: 600; padding: 3px 10px; border-radius: var(--dt-radius-sm); }
.type-badge.primary { background: var(--dt-primary-soft-2); color: var(--dt-primary); }
.type-badge.info { background: var(--dt-info-soft); color: var(--dt-info); }
.type-badge.gold { background: var(--dt-gold-soft); color: var(--dt-gold-dark); }
.type-badge.success { background: var(--dt-success-soft); color: var(--dt-success); }
.table-avatar { width: 36px; height: 36px; border-radius: 50%; color: #fff; font-weight: 600; display: inline-flex; align-items: center; justify-content: center; flex-shrink: 0; font-size: 0.875rem; background: #8d847c; }
.ga-tag { font-size: 0.6875rem; font-weight: 500; color: var(--dt-ink-3); background: var(--dt-muted); padding: 1px 8px; border-radius: var(--dt-radius-sm); }
.dp-name { font-size: 1.125rem; font-weight: 600; color: var(--dt-ink-1); display: flex; align-items: center; gap: 8px; }
.dp-sub { font-size: 0.75rem; color: var(--dt-ink-4); }
.dp-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 10px 16px; margin-bottom: 14px; }
.dp-row { display: flex; justify-content: space-between; font-size: 0.75rem; color: var(--dt-ink-3); }
.dp-row b { color: var(--dt-ink-1); }
.dp-section { font-size: 0.8125rem; font-weight: 600; color: var(--dt-ink-1); margin: 8px 0 6px; }
.id-row { display: flex; justify-content: space-between; font-size: 0.75rem; padding: 4px 0; color: var(--dt-ink-2); }
.id-row b { color: var(--dt-ink-1); }

.ai-suggest { background: var(--dt-primary-soft); border: 1px solid var(--dt-border); border-radius: var(--dt-radius-md); padding: 14px; margin: 12px 0; color: var(--dt-ink-2); }
.ai-suggest-head { display: flex; align-items: center; gap: 6px; font-size: 0.8125rem; font-weight: 600; color: var(--dt-primary); margin-bottom: 8px; }
.ai-btn { margin-left: auto; font-size: 0.6875rem; padding: 3px 10px; border-radius: var(--dt-radius-sm); border: 1px solid var(--dt-border-strong); background: var(--dt-card-solid); color: var(--dt-gold-dark); cursor: pointer; display: inline-flex; align-items: center; gap: 4px; }
.ai-btn:disabled { opacity: 0.6; }
.ai-hint { font-size: 0.75rem; color: var(--dt-ink-3); line-height: 1.6; }
.ai-body { font-size: 0.75rem; }
.ai-decision { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px; }
.ai-decision b { font-size: 0.875rem; }
.ai-decision.approve b { color: var(--dt-success); }
.ai-decision.reject b { color: var(--dt-error); }
.ai-decision.review b { color: var(--dt-warning); }
.ai-conf { font-size: 0.6875rem; color: var(--dt-ink-3); }
.conf-bar { height: 6px; border-radius: var(--dt-radius-sm); background: var(--dt-muted); margin-bottom: 10px; overflow: hidden; }
.conf-fill { height: 100%; border-radius: var(--dt-radius-sm); background: var(--dt-primary); }
.ai-list { display: flex; flex-direction: column; gap: 4px; margin-bottom: 6px; }
.ai-li { font-size: 0.75rem; line-height: 1.5; display: flex; align-items: center; gap: 5px; }
.ai-li.ok { color: var(--dt-success); }
.ai-li.warn { color: var(--dt-warning); }
.ai-advice { font-size: 0.75rem; color: var(--dt-ink-2); margin-bottom: 6px; }
.ai-src { font-size: 0.625rem; color: var(--dt-ink-4); }

.dp-actions { display: flex; gap: 10px; }
.dp-tip { font-size: 0.6875rem; color: var(--dt-ink-4); text-align: center; margin-top: 10px; }
.btn-reject { background: var(--dt-error); color: #fff; }
.reason-group { margin-bottom: 14px; }
.reason-label { font-size: 0.8125rem; font-weight: 600; color: var(--dt-ink-1); margin-bottom: 8px; }
.reason-chips { display: flex; flex-wrap: wrap; gap: 8px; }
</style>
