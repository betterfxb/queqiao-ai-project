<script setup lang="ts">
/**
 * 牵线详情 — 状态横幅（环形匹配度 + 阶段 stepper，可点击推进）+ 双方资料卡
 *            + AI 匹配分析（真实维度分）+ 牵线进展时间线（真实操作日志）
 *
 * 三种进入方式：
 *   /match/:id          → 直接打开指定配对
 *   /match?member=M001  → 智能推荐模式：为该会员挑选候选人并建立牵线
 *   /match              → 默认打开最近更新的一条配对
 */
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  fetchPairs,
  fetchPair,
  fetchPairStats,
  fetchMatchScore,
  fetchRecommendations,
  fetchMember,
  createPair,
  updatePair,
  deletePair,
  pairFeedback,
  pairMeeting,
  matchReplySuggest,
  fetchPairExplain,
  fetchCoachNextStep,
  fetchLogs,
  type Pair,
  type CoachNextStep,
} from '@/api'
import MatchScorePanel from './components/MatchScorePanel.vue'
import PairSideCard from './components/PairSideCard.vue'
import MatchAnalysis from './components/MatchAnalysis.vue'
import MatchTimeline from './components/MatchTimeline.vue'
import MatchFeedbackDialog from './components/MatchFeedbackDialog.vue'
import RecommendPanel from './components/RecommendPanel.vue'
import { STAGES, toPairPerson, type AiBar, type MatchStep, type PairPerson, type ProgressItem } from './types'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const pairs = ref<Pair[]>([])
const pairId = ref('')
const pair = ref<Pair | null>(null)
const male = ref<PairPerson | null>(null)
const female = ref<PairPerson | null>(null)
const aiBars = ref<AiBar[]>([])
const aiComment = ref('')
const timeline = ref<ProgressItem[]>([])
const stats = ref<Record<string, number>>({})

/* 解释型匹配报告 */
const explain = ref<{
  score: number
  dims: Record<string, number>
  fitPoints: string[]
  topics: string[]
  summary: string
  semanticKeywords: string[]
  semanticFit: number
} | null>(null)

/* AI 约会教练 */
const coach = ref<CoachNextStep | null>(null)

/* 推荐模式 */
const recMemberId = computed(() => String(route.query.member ?? '').trim())
const recMode = ref(false)
const recTarget = ref<any>(null)
const recList = ref<any[]>([])
const recLoading = ref(false)

const score = computed(() => Number(pair.value?.score ?? 0))
const steps = computed<MatchStep[]>(() => {
  const cur = STAGES.indexOf((pair.value?.stage ?? '初步推荐') as any)
  const idx = cur < 0 ? 0 : cur
  return STAGES.map((label, i) => ({
    label,
    state: i < idx ? 'done' : i === idx ? 'active' : 'pending',
  }))
})

const respOf = (text?: string, kind?: string, fallback = '等待回复') =>
  ({
    text: text || fallback,
    kind: (kind === 'success' ? 'success' : 'warning') as 'success' | 'warning',
    time: text ? '已回应' : '未回应',
  })

const loadTimeline = async (id: string) => {
  try {
    const logs: any[] = await fetchLogs(id)
    const colorOf = (a: string) =>
      a.includes('终止') ? 'var(--dt-danger)'
        : a.includes('约见') ? 'var(--dt-success)'
          : a.includes('建立') ? 'var(--dt-gold)'
            : 'var(--dt-primary)'
    timeline.value = logs.map((l) => ({
      text: `${l.action}${l.detail ? '：' + l.detail : ''}`,
      time: String(l.createdAt ?? '').replace('T', ' ').slice(5, 16),
      color: colorOf(String(l.action ?? '')),
    }))
    if (!timeline.value.length) {
      timeline.value = [{ text: '暂无进展记录，操作后将自动生成', time: '', color: 'var(--dt-ink-4)' }]
    }
  } catch {
    timeline.value = []
  }
}

const loadPair = async (id: string) => {
  if (!id) return
  loading.value = true
  try {
    const r: any = await fetchPair(id)
    pair.value = r.pair
    pairId.value = r.pair?.id ?? id
    male.value = toPairPerson(r.male, respOf(r.pair?.maleResponse, r.pair?.maleResponseKind))
    female.value = toPairPerson(r.female, respOf(r.pair?.femaleResponse, r.pair?.femaleResponseKind))
    aiComment.value = r.pair?.reason || '暂无 AI 评语，可点击「AI 沟通建议」生成话术。'
    loadTimeline(pairId.value)
    try {
      explain.value = await fetchPairExplain(pairId.value)
    } catch {
      explain.value = null
    }
    try {
      coach.value = await fetchCoachNextStep(pairId.value)
    } catch {
      coach.value = null
    }
    if (r.male?.id && r.female?.id) {
      try {
        const s: any = await fetchMatchScore(r.male.id, r.female.id)
        aiBars.value = Object.entries(s?.dimensions ?? {}).map(([label, pct]) => ({ label, pct: Number(pct) }))
        if (Array.isArray(s?.reasons) && s.reasons.length) aiComment.value = s.reasons.join('；')
        if (pair.value && s?.score) pair.value.score = Number(s.score)
      } catch { aiBars.value = [] }
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '配对详情加载失败')
  } finally {
    loading.value = false
  }
}

/** 推荐模式：加载目标会员 + 候选列表 */
const loadRecommend = async (memberId: string) => {
  recMode.value = true
  recLoading.value = true
  try {
    const [m, rec]: any[] = await Promise.all([
      fetchMember(memberId).catch(() => null),
      fetchRecommendations(memberId, 6),
    ])
    recTarget.value = rec?.target ?? m?.member ?? null
    recList.value = rec?.list ?? []
  } catch (e: any) {
    ElMessage.error(e?.message || '智能推荐加载失败')
    recList.value = []
  } finally {
    recLoading.value = false
  }
}

const boot = async () => {
  fetchPairStats().then((s: any) => { stats.value = s ?? {} }).catch(() => {})
  try {
    pairs.value = await fetchPairs({ size: 100 })
  } catch { pairs.value = [] }

  const idParam = String(route.params.id ?? '').trim()
  if (recMemberId.value) {
    await loadRecommend(recMemberId.value)
    return
  }
  recMode.value = false
  const target = idParam || pairs.value[0]?.id || ''
  if (target) await loadPair(target)
  else ElMessage.info('暂无牵线记录，请先从会员库发起牵线')
}

onMounted(boot)
watch(() => [route.params.id, route.query.member], boot)

/** 切换配对 */
const onSwitchPair = (id: string) => {
  if (!id || id === pairId.value) return
  router.push(`/match/${id}`)
}

/* —— 阶段推进（点击 stepper 直接改后端） —— */
const onStage = async (label: string) => {
  if (!pair.value) return
  if (label === pair.value.stage) return
  try {
    await ElMessageBox.confirm(`将牵线阶段调整为「${label}」？`, '阶段变更', { type: 'info' })
    const saved = await updatePair(pair.value.id, { stage: label })
    pair.value = { ...pair.value, ...saved }
    ElMessage.success(`阶段已更新为「${label}」`)
    loadTimeline(pair.value.id)
  } catch (e: any) {
    if (e !== 'cancel' && e !== 'close') ElMessage.error(e?.message || '阶段更新失败')
  }
}

/* —— 底部操作 —— */
const onRemind = async () => {
  if (!pair.value) return
  try {
    const { value } = await ElMessageBox.prompt('记录一条催进提醒（写入牵线备注与操作日志）', '发送提醒', {
      inputValue: '已电话提醒双方尽快沟通',
      inputPattern: /\S/,
      inputErrorMessage: '请输入提醒内容',
    })
    await updatePair(pair.value.id, { note: String(value) })
    ElMessage.success('提醒已记录')
    loadTimeline(pair.value.id)
  } catch (e: any) {
    if (e !== 'cancel' && e !== 'close') ElMessage.error(e?.message || '操作失败')
  }
}

const onSchedule = async () => {
  if (!pair.value) return
  try {
    const { value } = await ElMessageBox.prompt(
      '请输入约见信息，格式：日期 开始时间 地点（例：2026-08-15 15:00 静安嘉里中心咖啡厅）',
      '安排约见',
      {
        inputValue: `${new Date(Date.now() + 3 * 86400000).toISOString().slice(0, 10)} 15:00 待定`,
        inputPattern: /^\d{4}-\d{2}-\d{2}\s+\d{1,2}:\d{2}(\s+.+)?$/,
        inputErrorMessage: '格式应为：YYYY-MM-DD HH:mm 地点',
      },
    )
    const [date, startTime, ...rest] = String(value).trim().split(/\s+/)
    const endHour = String(Number(startTime.split(':')[0]) + 2).padStart(2, '0')
    const r: any = await pairMeeting(pair.value.id, {
      date,
      startTime,
      endTime: `${endHour}:${startTime.split(':')[1]}`,
      place: rest.join(' ') || '待定',
    })
    pair.value = { ...pair.value, ...(r?.pair ?? {}) }
    ElMessage.success('约见已安排，并已写入日程')
    loadTimeline(pair.value!.id)
  } catch (e: any) {
    if (e !== 'cancel' && e !== 'close') ElMessage.error(e?.message || '安排失败')
  }
}

const onFail = async () => {
  if (!pair.value) return
  try {
    const { value } = await ElMessageBox.prompt('请填写终止原因', '标记失败', {
      inputPattern: /\S/,
      inputErrorMessage: '请填写原因',
    })
    await deletePair(pair.value.id, String(value))
    ElMessage.success('已标记为终止')
    await loadPair(pair.value.id)
  } catch (e: any) {
    if (e !== 'cancel' && e !== 'close') ElMessage.error(e?.message || '操作失败')
  }
}

/* 记录反馈（结构化弹窗，1-D 闭环） */
const feedbackVisible = ref(false)
const feedbackSide = ref<'male' | 'female' | 'both'>('both')
const feedbackName = computed(() =>
  feedbackSide.value === 'male'
    ? (pair.value?.maleName ?? '')
    : feedbackSide.value === 'female'
      ? (pair.value?.femaleName ?? '')
      : '',
)
const onFeedback = (side: 'male' | 'female' | 'both' = 'both') => {
  if (!pair.value) return
  feedbackSide.value = side
  feedbackVisible.value = true
}
const onFeedbackSubmitted = () => {
  if (!pair.value) return
  loadPair(pair.value.id)
}

/* AI 沟通话术建议 */
const aiBusy = ref(false)
const onAiSuggest = async () => {
  if (!pair.value || aiBusy.value) return
  aiBusy.value = true
  try {
    const r: any = await matchReplySuggest({
      pairId: pair.value.id,
      male: pair.value.maleName,
      female: pair.value.femaleName,
      stage: pair.value.stage,
      score: pair.value.score,
    })
    const list: string[] = r?.suggestions ?? []
    if (!list.length) return ElMessage.warning('AI 暂未返回建议')
    await ElMessageBox.alert(
      `<div style="line-height:1.9">${list.map((s, i) => `${i + 1}. ${s}`).join('<br/>')}</div>`,
      `AI 沟通建议${r?.source === 'local-fallback' ? '（本地兜底）' : ''}`,
      { dangerouslyUseHTMLString: true, confirmButtonText: '知道了' },
    )
  } catch (e: any) {
    ElMessage.error(e?.message || 'AI 建议获取失败')
  } finally {
    aiBusy.value = false
  }
}

/* 推荐模式：建立牵线 */
const onCreatePair = async (candidate: any) => {
  const t = recTarget.value
  if (!t) return
  const male = t.gender === '男' ? t : candidate
  const female = t.gender === '男' ? candidate : t
  try {
    await ElMessageBox.confirm(`为「${male.name}」与「${female.name}」建立牵线？`, '建立牵线', { type: 'info' })
    const p: any = await createPair({ maleId: male.id, femaleId: female.id })
    ElMessage.success('牵线已建立')
    router.push(`/match/${p.id}`)
  } catch (e: any) {
    if (e !== 'cancel' && e !== 'close') ElMessage.error(e?.message || '建立牵线失败')
  }
}
</script>

<template>
  <div v-loading="loading" class="match-detail">
    <!-- 推荐模式 -->
    <RecommendPanel
      v-if="recMode"
      :target="recTarget"
      :list="recList"
      :loading="recLoading"
      @pick="onCreatePair"
      @back="router.push('/members')"
    />

    <template v-else-if="pair">
      <!-- 顶部：配对切换 + 操作 -->
      <div class="match-toolbar glass-card">
        <span class="tb-label">当前牵线</span>
        <el-select :model-value="pairId" size="small" style="width: 260px" filterable @change="onSwitchPair">
          <el-option
            v-for="p in pairs" :key="p.id"
            :label="`${p.maleName} × ${p.femaleName}（${p.status}）`" :value="p.id"
          />
        </el-select>
        <span class="tb-stat">进行中 {{ stats.ongoing ?? 0 }} · 待确认 {{ stats.waiting ?? 0 }} · 已约见 {{ stats.met ?? 0 }} · 成功率 {{ stats.successRate ?? 0 }}%</span>
        <div style="flex:1" />
        <button class="btn btn-outline" :disabled="aiBusy" @click="onAiSuggest">
          <app-icon name="sparkles" :size="15" />{{ aiBusy ? '生成中…' : 'AI 沟通建议' }}
        </button>
      </div>

      <!-- 状态横幅 -->
      <MatchScorePanel
        :score="score"
        :order-no="pair.id"
        :created-at="String(pair.createdAt ?? '').slice(0, 10)"
        :status="pair.status"
        :steps="steps"
        @stage="onStage"
      />

      <!-- 双方资料卡 -->
      <div class="pair-row">
        <PairSideCard v-if="male" :person="male" side-label="男方" @feedback="onFeedback('male')" />
        <div class="pair-connector"><app-icon name="heart" :size="20" /></div>
        <PairSideCard v-if="female" :person="female" side-label="女方" @feedback="onFeedback('female')" />
      </div>

      <!-- 底部双卡 -->
      <div class="bottom-row">
        <MatchAnalysis :bars="aiBars" :comment="aiComment" />
        <MatchTimeline :items="timeline" @remind="onRemind" @schedule="onSchedule" @feedback="onFeedback('both')" @fail="onFail" />
      </div>

      <!-- 解释型匹配报告 -->
      <section v-if="explain" class="explain-block">
        <h3 class="explain-h">
          <app-icon name="sparkles" :size="16" /> 为什么推荐 TA（解释型匹配报告）
        </h3>
        <p class="explain-summary">{{ explain.summary }}</p>
        <div class="explain-cols">
          <div class="explain-col">
            <div class="explain-col-title">契合点</div>
            <ul>
              <li v-for="(p, i) in explain.fitPoints" :key="i">{{ p }}</li>
            </ul>
          </div>
          <div class="explain-col">
            <div class="explain-col-title">建议沟通话题</div>
            <ul>
              <li v-for="(t, i) in explain.topics" :key="i">{{ t }}</li>
            </ul>
          </div>
        </div>

        <!-- 3-A 语义契合：离线文本相似度量化的深层契合（AI 宕机仍可用） -->
        <div v-if="explain?.semanticKeywords?.length" class="explain-semantic">
          <div class="explain-col-title">语义契合 · 共同关注（{{ explain.semanticFit }}%）</div>
          <div class="sem-chips">
            <span v-for="(k, i) in explain.semanticKeywords" :key="i" class="sem-chip">{{ k }}</span>
          </div>
        </div>
      </section>

      <!-- 3-B AI 约会教练：基于牵线进展的下一步建议 + 话题/穿搭话术库 -->
      <section v-if="coach" class="coach-block">
        <h3 class="coach-h">
          <app-icon name="sparkles" :size="16" /> AI 约会教练 · 下一步建议
          <span class="coach-stage">{{ coach.stageLabel }}</span>
          <span class="coach-window">建议窗口：{{ coach.actionWindow }}</span>
        </h3>

        <div class="coach-next">
          <span class="coach-next-tag">下一步</span>
          <span>{{ coach.nextStep }}</span>
        </div>

        <div class="coach-grid">
          <div class="coach-col">
            <div class="coach-col-title">话题库（按共同兴趣定制）</div>
            <div class="coach-chips">
              <span v-for="(t, i) in coach.topics" :key="i" class="coach-chip">{{ t }}</span>
            </div>
          </div>
          <div class="coach-col">
            <div class="coach-col-title">穿搭建议</div>
            <ul class="coach-list">
              <li v-for="(o, i) in coach.outfit" :key="i">{{ o }}</li>
            </ul>
          </div>
          <div class="coach-col">
            <div class="coach-col-title">破冰话术</div>
            <ul class="coach-list">
              <li v-for="(o, i) in coach.openers" :key="i">{{ o }}</li>
            </ul>
          </div>
          <div class="coach-col">
            <div class="coach-col-title">升温提问</div>
            <ul class="coach-list">
              <li v-for="(q, i) in coach.deepenQuestions" :key="i">{{ q }}</li>
            </ul>
          </div>
        </div>

        <div class="coach-foot">
          <div class="coach-tips">
            <span class="coach-foot-label">教练提示</span>
            <span v-for="(t, i) in coach.tips" :key="i" class="coach-tip">· {{ t }}</span>
          </div>
          <div v-if="coach.warnings?.length" class="coach-warn">
            <span class="coach-warn-tag">风险提示</span>
            <span v-for="(w, i) in coach.warnings" :key="i">{{ w }}</span>
          </div>
          <span class="coach-source" :class="coach.source === 'ai' ? 'ok' : 'fallback'">
            来源：{{ coach.source === 'ai' ? 'AI 实时' : '本地规则（AI 暂不可用）' }}
          </span>
        </div>
      </section>

      <!-- 1-D 反馈轻问卷 -->
      <MatchFeedbackDialog
        v-model="feedbackVisible"
        :pair-id="pairId"
        :side="feedbackSide"
        :member-name="feedbackName"
        @submitted="onFeedbackSubmitted"
      />
    </template>

    <el-empty v-else-if="!loading" description="暂无牵线记录，请先从会员库发起牵线" />
  </div>
</template>

<style scoped>
.match-detail { display: flex; flex-direction: column; gap: 24px; }

.match-toolbar { display: flex; align-items: center; gap: 14px; padding: 12px 18px; }
.tb-label { font-size: 0.8125rem; font-weight: 600; color: var(--dt-ink-2); }
.tb-stat { font-size: 0.75rem; color: var(--dt-ink-3); }

.pair-row { display: flex; align-items: stretch; gap: 0; }
.pair-connector {
  align-self: center; width: 48px; height: 48px; border-radius: 50%; flex-shrink: 0; margin: 0 12px;
  background: var(--dt-gold-gradient); color: #fff; display: flex; align-items: center; justify-content: center;
  box-shadow: var(--dt-shadow-gold-glow);
}

.bottom-row { display: grid; grid-template-columns: 1fr 1fr; gap: 24px; }

.explain-block {
  border: 1px solid var(--dt-border-gold, #f0e2c8);
  border-radius: var(--dt-radius-lg, 16px);
  background: var(--dt-gold-soft, #fdf8ef);
  padding: 18px 20px;
}
.explain-h {
  display: flex; align-items: center; gap: 8px;
  margin: 0 0 10px; font-size: 1rem; color: var(--dt-gold-dark, #9a6b1e);
}
.explain-summary { margin: 0 0 14px; color: var(--dt-ink-2); font-size: 0.8125rem; line-height: 1.6; }
.explain-cols { display: grid; grid-template-columns: 1fr 1fr; gap: 18px; }
.explain-col-title {
  font-size: 0.8125rem; font-weight: 700; color: var(--dt-ink-2);
  margin-bottom: 8px; padding-left: 9px; border-left: 3px solid var(--dt-gold, #d8a44a);
}
.explain-col ul { margin: 0; padding-left: 18px; }
.explain-col li { font-size: 0.8125rem; color: var(--dt-ink-3); line-height: 1.9; }
.explain-semantic { margin-top: 14px; }
.sem-chips { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 8px; }
.sem-chip {
  font-size: 0.75rem; padding: 3px 12px; border-radius: 999px;
  background: var(--dt-gold-soft, #fdf3df); color: var(--dt-gold-dark, #9a6b1e);
  border: 1px solid var(--dt-border-gold, #f0e2c8); font-weight: 600;
}

.coach-block {
  border: 1px solid var(--dt-border, #e6e8ee);
  border-radius: var(--dt-radius-lg, 16px);
  background: linear-gradient(180deg, #f3f8ff 0%, #ffffff 60%);
  padding: 18px 20px;
}
.coach-h {
  display: flex; align-items: center; gap: 8px; flex-wrap: wrap;
  margin: 0 0 12px; font-size: 1rem; color: var(--dt-primary, #2f6fed);
}
.coach-stage {
  font-size: 0.75rem; font-weight: 700; padding: 2px 10px; border-radius: 999px;
  background: var(--dt-primary-soft, #e8f0ff); color: var(--dt-primary, #2f6fed);
}
.coach-window { font-size: 0.75rem; color: var(--dt-ink-3); font-weight: 500; }
.coach-next {
  display: flex; gap: 10px; align-items: flex-start;
  background: var(--dt-primary-soft, #eef4ff); border-radius: 12px; padding: 12px 14px;
  color: var(--dt-ink-1); font-size: 0.875rem; line-height: 1.6; margin-bottom: 14px;
}
.coach-next-tag {
  flex-shrink: 0; font-size: 0.72rem; font-weight: 700; color: #fff;
  background: var(--dt-primary, #2f6fed); border-radius: 6px; padding: 2px 8px; margin-top: 2px;
}
.coach-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px 20px; }
.coach-col-title {
  font-size: 0.8125rem; font-weight: 700; color: var(--dt-ink-2);
  margin-bottom: 8px; padding-left: 9px; border-left: 3px solid var(--dt-primary, #2f6fed);
}
.coach-chips { display: flex; flex-wrap: wrap; gap: 8px; }
.coach-chip {
  font-size: 0.75rem; padding: 3px 12px; border-radius: 999px;
  background: #eef4ff; color: var(--dt-primary, #2f6fed); border: 1px solid #d6e4ff; font-weight: 600;
}
.coach-list { margin: 0; padding-left: 18px; }
.coach-list li { font-size: 0.8125rem; color: var(--dt-ink-3); line-height: 1.9; }
.coach-foot { margin-top: 14px; display: flex; flex-direction: column; gap: 8px; }
.coach-tips { display: flex; flex-wrap: wrap; gap: 6px 14px; align-items: center; }
.coach-foot-label { font-size: 0.75rem; font-weight: 700; color: var(--dt-ink-2); }
.coach-tip { font-size: 0.75rem; color: var(--dt-ink-3); }
.coach-warn {
  display: flex; gap: 8px; align-items: flex-start; flex-wrap: wrap;
  background: #fff5f0; border: 1px solid #ffd9c2; border-radius: 10px; padding: 8px 12px;
  color: #c0491f; font-size: 0.78rem; line-height: 1.6;
}
.coach-warn-tag {
  flex-shrink: 0; font-size: 0.7rem; font-weight: 700; color: #fff;
  background: #c0491f; border-radius: 6px; padding: 2px 8px;
}
.coach-source { font-size: 0.72rem; color: var(--dt-ink-4); align-self: flex-end; }
.coach-source.ok { color: #1f9d5b; }
.coach-source.fallback { color: #c0491f; }
</style>
