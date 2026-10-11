<script setup lang="ts">
import { ref, onMounted, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import VChart from '@/components/VChart.vue'
import {
  fetchChurn,
  fetchRenewal,
  fetchChurnScore,
  fetchFunnelAttribution,
  fetchAutomationRules,
  fetchOpsMetrics,
  fetchFunnel,
  toggleAutomationRule,
  runAutomation,
  applyAutomation,
  scanLowSatisfaction,
  fetchScreen,
  createEvent,
  createConversation,
  type ChurnItem,
  type RenewalItem,
  type ChurnScoreItem,
  type FunnelAttribution,
  type AutomationRule,
  type OpsMetrics,
  type FunnelData,
} from '@/api'

const churn = ref<ChurnItem[]>([])
const churnScore = ref<ChurnScoreItem[]>([])
const renewal = ref<RenewalItem[]>([])
const rules = ref<AutomationRule[]>([])
const metrics = ref<OpsMetrics | null>(null)
const funnel = ref<FunnelData | null>(null)
const funnelAttr = ref<FunnelAttribution | null>(null)
const funnelDays = ref<number>(0)
const loading = ref(false)
const runningId = ref<string | null>(null)
const applyingId = ref<string | null>(null)

/** 3-D 满意度闭环：来自大屏的满意度指标 + 扫描状态 */
const lowSatCount = ref(0)
const feedbackAvg = ref(0)
const scanning = ref(false)
const lastScan = ref<{ scanned: number; created: number; skipped: number } | null>(null)

const tomorrow = () => {
  const d = new Date(Date.now() + 86400000)
  return d.toISOString().slice(0, 10)
}

const fmtPct = (r: number) => ((r ?? 0) * 100).toFixed(1) + '%'

/** 整体成功率 / 付费率 是否达标（≥ 行业基准） */
const successOk = computed(() => {
  const f = funnel.value
  return !!f && f.overallSuccessRate >= f.benchmarkSuccessRate
})
const paidOk = computed(() => {
  const f = funnel.value
  return !!f && f.overallPaidRate >= f.benchmarkPaidRate
})

/** ECharts 漏斗配置 */
const funnelOption = computed<Record<string, any>>(() => {
  const stages = funnel.value?.stages ?? []
  const palette = ['#7d2b3d', '#93465a', '#a8863f', '#8d847c', '#5d1e2c', '#a8a29e']
  return {
    tooltip: {
      trigger: 'item',
      formatter: (p: any) => {
        const s = stages[p.dataIndex]
        return `${s.name}<br/>人数：${s.count}`
      },
    },
    series: [
      {
        type: 'funnel',
        left: '6%',
        right: '6%',
        top: 16,
        bottom: 16,
        minSize: '24%',
        maxSize: '100%',
        sort: 'descending',
        gap: 4,
        label: {
          show: true,
          position: 'inside',
          color: '#fff',
          fontSize: 13,
          lineHeight: 17,
          formatter: (p: any) => {
            const s = stages[p.dataIndex]
            const conv = s.prevCount ? fmtPct(s.conversion) : '100%'
            return `${s.name}\n${s.count} 人 · 转化 ${conv}`
          },
        },
        labelLine: { show: false },
        itemStyle: { borderColor: '#fff', borderWidth: 1 },
        data: stages.map((s, i) => ({
          name: s.name,
          value: s.count,
          itemStyle: { color: palette[i % palette.length] },
        })),
      },
    ],
  }
})

const load = async () => {
  loading.value = true
  try {
    const [c, r, cs, ru, m, f, fa] = await Promise.all([
      fetchChurn(),
      fetchRenewal(),
      fetchChurnScore(),
      fetchAutomationRules(),
      fetchOpsMetrics(),
      fetchFunnel(funnelDays.value),
      fetchFunnelAttribution(),
    ])
    churn.value = c.list || []
    renewal.value = r.list || []
    churnScore.value = cs.list || []
    rules.value = ru || []
    metrics.value = m || null
    funnel.value = f || null
    funnelAttr.value = fa || null
    try {
      const sc = await fetchScreen()
      lowSatCount.value = sc?.satisfaction?.lowSatisfactionCount ?? 0
      feedbackAvg.value = sc?.satisfaction?.feedbackAvg ?? 0
    } catch (e) {
      // 大屏数据非关键，失败不影响主流程
    }
  } catch (e) {
    ElMessage.error('加载智能运营数据失败：' + (e as Error).message)
  } finally {
    loading.value = false
  }
}

/** 3-D 满意度闭环：扫描近 30 天低分反馈，自动建跟进 + 红娘改进建议（接 smart-ops 自动化） */
const runSatisfactionScan = async () => {
  scanning.value = true
  try {
    const res = await scanLowSatisfaction(30)
    lastScan.value = { scanned: res.scanned, created: res.created, skipped: res.skipped }
    ElMessage.success(`扫描 ${res.scanned} 条低分反馈，新建跟进 ${res.created} 条，跳过 ${res.skipped} 条`)
    await load()
  } catch (e: any) {
    ElMessage.error('满意度扫描失败：' + (e?.message ?? '未知错误'))
  } finally {
    scanning.value = false
  }
}

/** 切换时间窗重新拉取漏斗 */
watch(funnelDays, async () => {
  try {
    funnel.value = (await fetchFunnel(funnelDays.value)) || null
  } catch (e) {
    ElMessage.error('加载转化漏斗失败：' + (e as Error).message)
  }
})

onMounted(load)

const memberName = (m: Record<string, unknown>) => (m?.name as string) || '—'

/** 金额千分位 */
const formatMoney = (v: number) => (v ?? 0).toLocaleString('zh-CN')

/** 流失预警 → 生成跟进任务（真实写入日程 + 会话） */
const genFollowup = async (it: ChurnItem) => {
  const m = it.member
  await createEvent({
    title: '流失跟进 · ' + memberName(m),
    type: '流失跟进',
    date: tomorrow(),
    startTime: '10:00',
    endTime: '11:00',
    place: '线上/门店',
    agent: m.agent,
    status: '待确认',
    remark: it.reason,
    memberIds: m.id,
    memberNames: m.name,
  })
  await createConversation(String(m.id))
  ElMessage.success('已为 ' + memberName(m) + ' 生成跟进任务（日程 + 会话）')
  await load()
}

/** 续费提醒 → 生成续费提醒（真实写入日程 + 会话） */
const genRenewal = async (it: RenewalItem) => {
  const m = it.member
  await createEvent({
    title: '续费提醒 · ' + memberName(m),
    type: '续费提醒',
    date: it.expire,
    startTime: '10:00',
    endTime: '10:15',
    place: '线上',
    agent: m.agent,
    status: '待确认',
    remark: '剩 ' + it.daysLeft + ' 天到期',
    memberIds: m.id,
    memberNames: m.name,
  })
  await createConversation(String(m.id))
  ElMessage.success('已为 ' + memberName(m) + ' 生成续费提醒（日程 + 会话）')
  await load()
}

/** 1-F 预警→动作自动编排：为高流失风险会员一键生成跟进并指派红娘 */
const genAutoFollowup = async (it: ChurnScoreItem) => {
  const m = it.member
  applyingId.value = String(m.id)
  try {
    const res = await applyAutomation(String(m.id))
    if (res.dup) {
      ElMessage.info(memberName(m) + ' 今日已生成自动跟进（指派红娘 ' + (res.agent || '—') + '）')
    } else {
      ElMessage.success('已为 ' + memberName(m) + ' 自动生成跟进并指派红娘 ' + (res.agent || '—'))
    }
    await load()
  } catch (e) {
    ElMessage.error('自动编排失败：' + (e as Error).message)
  } finally {
    applyingId.value = null
  }
}

const toggleRule = async (r: AutomationRule) => {
  const next = !r.enabled
  try {
    await toggleAutomationRule(r.id, next)
    r.enabled = next
    ElMessage.success((next ? '已启用' : '已停用') + '规则：' + r.name)
  } catch (e) {
    ElMessage.error('切换失败：' + (e as Error).message)
  }
}

const runRule = async (r: AutomationRule) => {
  if (!r.enabled) {
    ElMessage.warning('请先启用该规则')
    return
  }
  runningId.value = r.id
  try {
    const res = await runAutomation(r.id)
    ElMessage.success('「' + r.name + '」执行完成，生成 ' + (res.created ?? 0) + ' 项动作')
    await load()
  } catch (e) {
    ElMessage.error('执行失败：' + (e as Error).message)
  } finally {
    runningId.value = null
  }
}
</script>

<template>
  <div class="smart-ops" v-loading="loading">
    <header class="so-head">
      <div>
        <h2>智能运营中心</h2>
        <p class="so-sub">基于真实会员数据驱动预警、续费与自动化，让运营动作可一键落地。</p>
      </div>
      <el-button text type="primary" @click="load">刷新</el-button>
    </header>

    <!-- 运营成效：让运营看见商业结果 -->
    <section class="so-metrics" v-if="metrics">
      <div class="so-metric">
        <div class="so-metric-val danger">{{ metrics.churnCount }}</div>
        <div class="so-metric-label">流失风险会员</div>
        <div class="so-metric-sub">近 {{ metrics.churnDays }} 天无活跃</div>
      </div>
      <div class="so-metric">
        <div class="so-metric-val warn">{{ metrics.renewalDueCount }}</div>
        <div class="so-metric-label">临近续费到期</div>
        <div class="so-metric-sub">近 {{ metrics.renewalDays }} 天内</div>
      </div>
      <div class="so-metric">
        <div class="so-metric-val success">¥{{ formatMoney(metrics.renewalRecovered30d) }}</div>
        <div class="so-metric-label">30天续费金额</div>
        <div class="so-metric-sub">来自已完成续费订单</div>
      </div>
      <div class="so-metric">
        <div class="so-metric-val primary">{{ metrics.referralRate }}%</div>
        <div class="so-metric-label">转介绍率</div>
        <div class="so-metric-sub">{{ metrics.referralCount }} 位来自转介绍</div>
      </div>
      <div class="so-metric">
        <div class="so-metric-val">{{ metrics.leadTotal }}</div>
        <div class="so-metric-label">在管线索总数</div>
        <div class="so-metric-sub">CRM 全量线索</div>
      </div>
    </section>
    <el-alert
      v-else-if="!loading"
      type="info"
      :closable="false"
      title="运营成效数据暂不可用，请确认后端 /api/ops/metrics 已就绪"
      show-icon
    />

    <!-- 转化获客漏斗：让运营看见从注册到成功的逐级转化 -->
    <el-card class="so-funnel" shadow="never" v-if="funnel">
      <template #header>
        <div class="so-card-head">
          <span class="so-dot primary"></span>
          <span>转化获客漏斗</span>
          <span class="so-funnel-window">数据周期：{{ funnel.window }}</span>
          <el-radio-group v-model="funnelDays" size="small" class="so-funnel-range">
            <el-radio-button :value="7">近7天</el-radio-button>
            <el-radio-button :value="30">近30天</el-radio-button>
            <el-radio-button :value="90">近90天</el-radio-button>
            <el-radio-button :value="0">全部</el-radio-button>
          </el-radio-group>
        </div>
      </template>
      <div class="so-funnel-body">
        <div class="so-funnel-chart">
          <VChart :option="funnelOption" />
        </div>
        <div class="so-funnel-stats">
          <div class="so-stat">
            <div class="so-stat-label">整体成功率（成功/注册）</div>
            <div class="so-stat-val" :class="successOk ? 'ok' : 'low'">
              {{ fmtPct(funnel.overallSuccessRate) }}
              <el-tag size="small" :type="successOk ? 'success' : 'danger'" effect="light">
                {{ successOk ? '达标' : '偏低' }}
              </el-tag>
            </div>
            <div class="so-stat-sub">行业基准 {{ fmtPct(funnel.benchmarkSuccessRate) }}</div>
          </div>
          <div class="so-stat">
            <div class="so-stat-label">整体付费率（付费/注册）</div>
            <div class="so-stat-val" :class="paidOk ? 'ok' : 'low'">
              {{ fmtPct(funnel.overallPaidRate) }}
              <el-tag size="small" :type="paidOk ? 'success' : 'danger'" effect="light">
                {{ paidOk ? '达标' : '偏低' }}
              </el-tag>
            </div>
            <div class="so-stat-sub">行业基准 {{ fmtPct(funnel.benchmarkPaidRate) }}</div>
          </div>
          <div class="so-conv-list">
            <div class="so-conv-title">层间转化率</div>
            <div class="so-conv-row" v-for="(s, i) in funnel.stages" :key="s.name">
              <span class="so-conv-name">{{ s.name }}</span>
              <span class="so-conv-count">{{ s.count }} 人</span>
              <span class="so-conv-rate" v-if="i > 0">{{ fmtPct(s.conversion) }}</span>
              <span class="so-conv-rate muted" v-else>起点</span>
            </div>
          </div>
        </div>
        <div class="so-funnel-attr" v-if="funnelAttr">
          <div class="so-attr-title">转化掉点归因</div>
          <div class="so-attr-worst">最大掉点：<b>{{ funnelAttr.worstStep }}</b>（流失 {{ funnelAttr.worstDrop }} 人）</div>
          <div class="so-attr-reasons">
            <el-tag v-for="(r, i) in funnelAttr.reasons" :key="i" size="small" type="warning" effect="plain">{{ r }}</el-tag>
          </div>
        </div>
      </div>
    </el-card>
    <el-alert
      v-else-if="!loading"
      type="info"
      :closable="false"
      title="转化漏斗数据暂不可用，请确认后端 /api/ops/funnel 已就绪"
      show-icon
    />

    <div class="so-grid">
      <!-- 流失预警 -->
      <el-card class="so-card" shadow="never">
        <template #header>
          <div class="so-card-head">
            <span class="so-dot danger"></span>
            <span>流失预警</span>
            <el-tag type="danger" effect="light">{{ churn.length }}</el-tag>
          </div>
        </template>
        <el-empty v-if="!churn.length" description="当前无流失风险会员" />
        <ul v-else class="so-list">
          <li v-for="(it, i) in churn" :key="i" class="so-item">
            <div class="so-item-main">
              <div class="so-name">{{ memberName(it.member) }}
                <span class="so-meta">{{ it.member.gender }} · {{ it.member.age }} · {{ it.member.level }}</span>
              </div>
              <div class="so-reason">
                <el-tag size="small" :type="it.feedback === '差评' ? 'danger' : 'warning'" effect="plain">
                  {{ it.reason }}
                </el-tag>
              </div>
            </div>
            <el-button size="small" type="danger" plain @click="genFollowup(it)">生成跟进</el-button>
          </li>
        </ul>
      </el-card>

      <!-- 预测式流失预警（提前14天） -->
      <el-card class="so-card" shadow="never">
        <template #header>
          <div class="so-card-head">
            <span class="so-dot danger"></span>
            <span>预测式流失预警</span>
            <el-tag type="danger" effect="light">{{ churnScore.length }}</el-tag>
            <span class="so-meta" style="margin-left:auto">提前 14 天预测</span>
          </div>
        </template>
        <el-empty v-if="!churnScore.length" description="当前无中高风险会员" />
        <ul v-else class="so-list">
          <li v-for="(it, i) in churnScore" :key="i" class="so-item">
            <div class="so-item-main">
              <div class="so-name">{{ memberName(it.member) }}
                <span class="so-meta">{{ it.member.gender }} · {{ it.member.age }} · {{ it.member.level }}</span>
                <el-tag size="small" :type="it.level === '高' ? 'danger' : 'warning'" effect="plain">风险 {{ (it.score * 100).toFixed(0) }}%</el-tag>
              </div>
              <div class="so-reason">
                <el-tag size="small" type="info" effect="plain">提前14天</el-tag>
                <el-tag v-for="(r, ri) in it.reasons" :key="ri" size="small" :type="it.level === '高' ? 'danger' : 'warning'" effect="plain">{{ r }}</el-tag>
              </div>
            </div>
            <el-button size="small" type="danger" plain :loading="applyingId === it.member.id" @click="genAutoFollowup(it)">自动生成并指派</el-button>
          </li>
        </ul>
      </el-card>

      <!-- 3-D 满意度闭环（接 smart-ops 自动化） -->
      <el-card class="so-card" shadow="never">
        <template #header>
          <div class="so-card-head">
            <span class="so-dot primary"></span>
            <span>满意度闭环</span>
            <el-tag type="danger" effect="light">{{ lowSatCount }}</el-tag>
            <span class="so-meta" style="margin-left:auto">反馈均分 {{ feedbackAvg }}</span>
          </div>
        </template>
        <div class="sat-body">
          <p class="sat-desc">
            会员提交低分（≤2/5）牵线反馈时，系统自动为该会员建跟进（含红娘改进建议）并生成回访日程，指派其红娘，
            形成「低分 → 跟进 → 改进」真实闭环。下方按钮可批量补扫近 30 天低分反馈。
          </p>
          <div class="sat-actions">
            <el-button type="primary" :loading="scanning" @click="runSatisfactionScan">扫描并自动建跟进</el-button>
            <span v-if="lastScan" class="sat-result">
              最近扫描：{{ lastScan.scanned }} 条低分 · 新建 {{ lastScan.created }} · 跳过 {{ lastScan.skipped }}
            </span>
          </div>
        </div>
      </el-card>

      <!-- 续费提醒 -->
      <el-card class="so-card" shadow="never">
        <template #header>
          <div class="so-card-head">
            <span class="so-dot warn"></span>
            <span>续费提醒</span>
            <el-tag type="warning" effect="light">{{ renewal.length }}</el-tag>
          </div>
        </template>
        <el-empty v-if="!renewal.length" description="未来 15 天无到期会员" />
        <ul v-else class="so-list">
          <li v-for="(it, i) in renewal" :key="i" class="so-item">
            <div class="so-item-main">
              <div class="so-name">{{ memberName(it.member) }}
                <span class="so-meta">{{ it.member.level }} · 红娘 {{ it.member.agent }}</span>
              </div>
              <div class="so-reason">
                <el-tag size="small" type="warning" effect="plain">{{ it.expire }} 到期（剩 {{ it.daysLeft }} 天）</el-tag>
              </div>
            </div>
            <el-button size="small" type="warning" plain @click="genRenewal(it)">生成提醒</el-button>
          </li>
        </ul>
      </el-card>

      <!-- 营销自动化 -->
      <el-card class="so-card" shadow="never">
        <template #header>
          <div class="so-card-head">
            <span class="so-dot primary"></span>
            <span>营销自动化</span>
          </div>
        </template>
        <ul class="so-list">
          <li v-for="r in rules" :key="r.id" class="so-item so-rule">
            <div class="so-item-main">
              <div class="so-name">{{ r.name }}
                <span class="so-meta">{{ r.desc }}</span>
              </div>
              <div class="so-reason so-lastrun">最近执行：{{ r.lastRun || '从未' }}</div>
            </div>
            <div class="so-rule-actions">
              <el-switch :model-value="r.enabled" @change="toggleRule(r)" />
              <el-button size="small" type="primary" :loading="runningId === r.id" @click="runRule(r)">
                执行
              </el-button>
            </div>
          </li>
        </ul>
      </el-card>

      <!-- 红娘人效：把自动化动作与商业结果挂钩 -->
      <el-card class="so-card" shadow="never">
        <template #header>
          <div class="so-card-head">
            <span class="so-dot primary"></span>
            <span>红娘人效</span>
            <el-tag type="info" effect="plain" v-if="metrics">本月</el-tag>
          </div>
        </template>
        <el-empty v-if="!metrics || !metrics.agentEfficiency?.length" description="暂无红娘人效数据" />
        <el-table v-else :data="metrics.agentEfficiency" size="small" style="width: 100%">
          <el-table-column prop="name" label="红娘" min-width="90" />
          <el-table-column prop="monthMatch" label="月牵线" width="80" align="center" />
          <el-table-column prop="monthRevenue" label="月营收(元)" width="100" align="right">
            <template #default="{ row }">{{ row.monthRevenue.toLocaleString() }}</template>
          </el-table-column>
          <el-table-column prop="successRate" label="成功率" width="80" align="center">
            <template #default="{ row }">{{ row.successRate }}%</template>
          </el-table-column>
        </el-table>
      </el-card>
    </div>
  </div>
</template>

<style scoped>
.smart-ops {
  padding: 20px 24px 40px;
  min-height: 100%;
}
.sat-body { padding: 4px 2px; }
.sat-desc { margin: 0 0 14px; color: var(--dt-ink-2); font-size: 13px; line-height: 1.7; }
.sat-actions { display: flex; align-items: center; gap: 14px; flex-wrap: wrap; }
.sat-result { color: var(--dt-ink-3); font-size: 13px; }
.so-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 18px;
}
.so-head h2 {
  margin: 0;
  font-size: 1.375rem;
  color: var(--dt-ink-1);
}
.so-sub {
  margin: 4px 0 0;
  color: var(--dt-ink-3);
  font-size: 0.8125rem;
}
.so-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
  gap: 18px;
  align-items: start;
}
.so-metrics {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 14px;
  margin-bottom: 18px;
}
.so-metric {
  padding: 16px 18px;
  border-radius: var(--dt-radius-md);
  border: 1px solid var(--dt-border);
  background: var(--dt-card-solid);
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.so-funnel {
  border-radius: var(--dt-radius-lg);
  border: 1px solid var(--dt-border);
  background: var(--dt-card-solid);
  margin-bottom: 18px;
}
.so-funnel-window {
  margin-left: 4px;
  font-size: 0.75rem;
  font-weight: 400;
  color: var(--dt-ink-3);
}
.so-funnel-range {
  margin-left: auto;
}
.so-funnel-body {
  display: grid;
  grid-template-columns: minmax(360px, 1.4fr) minmax(280px, 1fr);
  gap: 18px;
  align-items: stretch;
}
.so-funnel-chart {
  min-height: 320px;
  height: 360px;
}
.so-funnel-stats {
  display: flex;
  flex-direction: column;
  gap: 14px;
  justify-content: center;
}
.so-stat {
  padding: 14px 16px;
  border: 1px solid var(--dt-border);
  border-radius: var(--dt-radius-md);
  background: var(--dt-card-solid);
}
.so-stat-label {
  font-size: 0.8125rem;
  color: var(--dt-ink-3);
}
.so-stat-val {
  margin: 6px 0 4px;
  font-size: 1.625rem;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--dt-ink-1);
}
.so-stat-val.ok { color: var(--dt-success); }
.so-stat-val.low { color: var(--dt-error); }
.so-stat-sub {
  font-size: 0.75rem;
  color: var(--dt-ink-3);
}
.so-conv-list {
  border: 1px solid var(--dt-border);
  border-radius: var(--dt-radius-md);
  padding: 12px 14px;
  background: var(--dt-card-solid);
}
.so-conv-title {
  font-size: 0.8125rem;
  font-weight: 600;
  color: var(--dt-ink-1);
  margin-bottom: 8px;
}
.so-conv-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 0;
  font-size: 0.8125rem;
  border-top: 1px dashed var(--dt-border);
}
.so-conv-row:first-of-type { border-top: none; }
.so-conv-name { font-weight: 600; color: var(--dt-ink-1); }
.so-conv-count { margin-left: auto; color: var(--dt-ink-3); }
.so-conv-rate { color: var(--dt-primary); font-weight: 600; }
.so-conv-rate.muted { color: var(--dt-ink-4); font-weight: 400; }
.so-funnel-attr {
  margin-top: 16px;
  padding: 14px 16px;
  border: 1px dashed var(--dt-border-gold);
  border-radius: var(--dt-radius-md);
  background: var(--dt-gold-soft);
}
.so-attr-title {
  font-size: 0.8125rem;
  font-weight: 600;
  color: var(--dt-ink-1);
  margin-bottom: 8px;
}
.so-attr-worst { font-size: 0.875rem; color: var(--dt-ink-2); margin-bottom: 8px; }
.so-attr-worst b { color: var(--dt-error); }
.so-attr-reasons { display: flex; flex-wrap: wrap; gap: 8px; }
.so-metric-val {
  font-size: 1.75rem;
  font-weight: 600;
  line-height: 1.1;
  color: var(--dt-ink-1);
}
.so-metric-val.danger { color: var(--dt-error); }
.so-metric-val.warn { color: var(--dt-warning); }
.so-metric-val.primary { color: var(--dt-primary); }
.so-metric-val.success { color: var(--dt-success); }
.so-metric-label {
  font-size: 0.875rem;
  font-weight: 600;
  color: var(--dt-ink-1);
}
.so-metric-sub {
  font-size: 0.75rem;
  color: var(--dt-ink-3);
}
.so-card {
  border-radius: var(--dt-radius-lg);
  border: 1px solid var(--dt-border);
  background: var(--dt-card-solid);
}
.so-card-head {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  font-size: 0.9375rem;
}
.so-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}
.so-dot.danger { background: var(--dt-error); }
.so-dot.warn { background: var(--dt-warning); }
.so-dot.primary { background: var(--dt-primary); }
.so-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.so-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px;
  border: 1px solid var(--dt-border);
  border-radius: var(--dt-radius-md);
  background: var(--dt-card-solid);
}
.so-item-main { min-width: 0; }
.so-name {
  font-weight: 600;
  font-size: 0.875rem;
  color: var(--dt-ink-1);
}
.so-meta {
  margin-left: 8px;
  font-weight: 400;
  font-size: 0.75rem;
  color: var(--dt-ink-3);
}
.so-reason { margin-top: 6px; }
.so-lastrun { font-size: 0.75rem; color: var(--dt-ink-3); }
.so-rule-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}
</style>
