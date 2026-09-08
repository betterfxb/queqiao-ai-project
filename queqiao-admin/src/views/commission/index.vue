<script setup lang="ts">
/**
 * 佣金结算中心
 *  - 红娘（AGENT）：仅可见「我的佣金」+ 个人汇总。
 *  - 主管 / 管理员：可见「团队佣金」「结算单」「提现」三栏，并展示团队「分佣占营收比」。
 * 分佣由后端在「牵线成功 / 续费支付完成」时按系统设置比例自动计提，这里只做查询与结算动作。
 */
import { ref, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import {
  fetchMyCommission,
  fetchTeamCommission,
  fetchCommissionSummary,
  settleCommission,
  withdrawCommission,
  type CommissionRecord,
  type CommissionSummary,
} from '@/api'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const isAgent = computed(() => auth.role === 'AGENT')
const isManager = computed(() => auth.role === 'MANAGER' || auth.role === 'ADMIN')

const view = ref<'mine' | 'team' | 'settle' | 'withdraw'>(isAgent.value ? 'mine' : 'team')

const loading = ref(false)
const records = ref<CommissionRecord[]>([])
const summary = ref<CommissionSummary | null>(null)

const agentName = ref('')
const settling = ref(false)
const withdrawing = ref(false)
const lastSettle = ref<{ batchNo: string; count: number; total: number } | null>(null)
const lastWithdraw = ref<{ count: number; total: number } | null>(null)

const statusType = (s: string) => (s === '待结算' ? 'warning' : s === '已结算' ? 'primary' : 'success')
const typeType = (t: string) => (t === '成功' ? 'success' : 'info')
const fmt = (n: number | undefined) => (n == null ? '0.00' : n.toFixed(2))

const loadSummary = async () => {
  try {
    summary.value = await fetchCommissionSummary(isAgent.value ? 'mine' : 'team', isAgent.value ? undefined : agentName.value || undefined)
  } catch (e: any) {
    summary.value = null
  }
}

const loadRecords = async () => {
  loading.value = true
  try {
    records.value = isAgent.value
      ? await fetchMyCommission()
      : await fetchTeamCommission(agentName.value || undefined)
    await loadSummary()
  } catch (e: any) {
    ElMessage.error('加载佣金数据失败：' + (e?.message ?? '未知错误'))
  } finally {
    loading.value = false
  }
}

const doSettle = async () => {
  settling.value = true
  try {
    const r = await settleCommission({ agentName: agentName.value || undefined })
    lastSettle.value = r
    lastWithdraw.value = null
    ElMessage.success(`已生成结算单 ${r.batchNo}，共 ${r.count} 笔 ¥${fmt(r.total)}`)
    await loadRecords()
  } catch (e: any) {
    ElMessage.error('结算失败：' + (e?.message ?? '未知错误'))
  } finally {
    settling.value = false
  }
}

const doWithdraw = async () => {
  withdrawing.value = true
  try {
    const r = await withdrawCommission({ agentName: agentName.value || undefined })
    lastWithdraw.value = r
    lastSettle.value = null
    ElMessage.success(`已提现 ${r.count} 笔 ¥${fmt(r.total)}`)
    await loadRecords()
  } catch (e: any) {
    ElMessage.error('提现失败：' + (e?.message ?? '未知错误'))
  } finally {
    withdrawing.value = false
  }
}

onMounted(loadRecords)
</script>

<template>
  <div class="commission-page">
    <div class="page-head">
      <div>
        <h1>佣金结算中心</h1>
        <p>分佣在「牵线成功 / 续费支付完成」时按系统设置比例自动计提，可生成结算单与提现记录。</p>
      </div>
    </div>

    <!-- 汇总卡片 -->
    <div class="summary-grid" v-loading="loading">
      <div class="sum-card" v-if="summary">
        <span class="sum-label">待结算</span>
        <span class="sum-value warn">¥{{ fmt(summary.pendingAmount) }}</span>
        <span class="sum-sub">{{ summary.pendingCount }} 笔</span>
      </div>
      <div class="sum-card" v-if="summary">
        <span class="sum-label">已结算</span>
        <span class="sum-value info">¥{{ fmt(summary.settledAmount) }}</span>
        <span class="sum-sub">{{ summary.settledCount }} 笔</span>
      </div>
      <div class="sum-card" v-if="summary">
        <span class="sum-label">已提现</span>
        <span class="sum-value ok">¥{{ fmt(summary.withdrawnAmount) }}</span>
        <span class="sum-sub">{{ summary.withdrawnCount }} 笔</span>
      </div>
      <div class="sum-card" v-if="summary && !isAgent">
        <span class="sum-label">分佣占营收比</span>
        <span class="sum-value">{{ summary.commissionRatio ?? 0 }}%</span>
        <span class="sum-sub">营收 ¥{{ fmt(summary.revenueTotal) }}</span>
      </div>
    </div>

    <!-- 红娘：仅我的佣金 -->
    <el-tabs v-model="view" class="tabs">
      <el-tab-pane v-if="isAgent" label="我的佣金" name="mine" />
      <template v-else>
        <el-tab-pane label="团队佣金" name="team" />
        <el-tab-pane label="结算单" name="settle" />
        <el-tab-pane label="提现" name="withdraw" />
      </template>
    </el-tabs>

    <div class="toolbar" v-if="!isAgent">
      <el-input v-model="agentName" placeholder="按红娘姓名筛选（留空=全部）" clearable style="width: 260px" @keyup.enter="loadRecords" />
      <el-button type="primary" plain @click="loadRecords">查询</el-button>
    </div>

    <!-- 佣金明细表 -->
    <el-table v-if="view !== 'settle' && view !== 'withdraw'" :data="records" v-loading="loading" stripe border>
      <el-table-column prop="agentName" label="红娘" width="110" />
      <el-table-column prop="type" label="类型" width="90">
        <template #default="{ row }"><el-tag :type="typeType(row.type)" size="small">{{ row.type }}</el-tag></template>
      </el-table-column>
      <el-table-column prop="orderAmount" label="计提基数" width="120">
        <template #default="{ row }">¥{{ fmt(row.orderAmount) }}</template>
      </el-table-column>
      <el-table-column prop="rate" label="比例" width="80">
        <template #default="{ row }">{{ row.rate }}%</template>
      </el-table-column>
      <el-table-column prop="amount" label="佣金" width="120">
        <template #default="{ row }"><b>¥{{ fmt(row.amount) }}</b></template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }"><el-tag :type="statusType(row.status)" size="small">{{ row.status }}</el-tag></template>
      </el-table-column>
      <el-table-column prop="batchNo" label="结算批次" width="180" />
      <el-table-column prop="createdAt" label="计提时间" min-width="160" />
    </el-table>

    <!-- 结算单 -->
    <div v-else-if="view === 'settle'" class="action-panel">
      <p class="hint">将「待结算」佣金转为「已结算」并生成结算单批次号。可按红娘姓名筛选范围。</p>
      <div class="action-row">
        <el-button type="primary" :loading="settling" @click="doSettle">生成结算单</el-button>
      </div>
      <el-alert v-if="lastSettle" type="success" :closable="false"
        :title="`结算单 ${lastSettle.batchNo} 已生成`"
        :description="`共 ${lastSettle.count} 笔，金额 ¥${fmt(lastSettle.total)}`" show-icon />
    </div>

    <!-- 提现 -->
    <div v-else class="action-panel">
      <p class="hint">将「已结算」佣金转为「已提现」，记录红娘实际到账。可按红娘姓名筛选范围。</p>
      <div class="action-row">
        <el-button type="success" :loading="withdrawing" @click="doWithdraw">确认提现</el-button>
      </div>
      <el-alert v-if="lastWithdraw" type="success" :closable="false"
        :title="`已提现 ${lastWithdraw.count} 笔`"
        :description="`金额 ¥${fmt(lastWithdraw.total)}`" show-icon />
    </div>
  </div>
</template>

<style scoped>
.commission-page { display: flex; flex-direction: column; gap: 20px; }
.page-head h1 { font-size: 1.375rem; font-weight: 800; color: var(--dt-ink-1); margin: 0; }
.page-head p { margin: 6px 0 0; color: var(--dt-ink-3); font-size: 0.8125rem; }

.summary-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 16px; }
.sum-card {
  background: var(--dt-card, #fff); border: 1px solid var(--dt-border, #ececf2);
  border-radius: var(--dt-radius-lg, 16px); padding: 16px 18px;
  display: flex; flex-direction: column; gap: 4px;
}
.sum-label { font-size: 0.7813rem; color: var(--dt-ink-3); }
.sum-value { font-size: 1.5rem; font-weight: 800; color: var(--dt-ink-1); }
.sum-value.warn { color: #d98800; } .sum-value.info { color: var(--dt-primary); } .sum-value.ok { color: #1aa86b; }
.sum-sub { font-size: 0.75rem; color: var(--dt-ink-3); }

.tabs { margin-top: 0; }
.toolbar { display: flex; gap: 12px; }
.action-panel { display: flex; flex-direction: column; gap: 14px; }
.action-panel .hint { color: var(--dt-ink-3); font-size: 0.8125rem; margin: 0; }
.action-row { margin: 0; }
</style>
