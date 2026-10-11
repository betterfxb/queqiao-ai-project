<script setup lang="ts">
/**
 * 预付资金存管（3-D #260）+ 支付与交易闭环（阶段四 4-A #275）
 *  - 高客单价套餐预付款进入存管，履约后放款、争议时退款，全程状态留痕（退费透明）
 *  - 数字人民币 / 银行存管双通道（离线桩，预留真实网关接入）
 *  - 4-A 闭环：支付成功（FinanceService.pay）自动建托管单（EscrowService.autoHold），
 *    并回写 financeOrderId，本页「关联收款订单」列即可溯源「套餐购买 → 资金托管」
 * 权限：财务操作需主管及以上。
 */
import { ref, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  fetchEscrows,
  fetchEscrowStats,
  holdEscrow,
  releaseEscrow,
  refundEscrow,
  type EscrowOrder,
  type EscrowStats,
} from '@/api/modules/escrow'

const orders = ref<EscrowOrder[]>([])
const stats = ref<EscrowStats | null>(null)
const loading = ref(false)

const keyword = ref('')

const load = async () => {
  loading.value = true
  try {
    const [os, st] = await Promise.all([fetchEscrows(), fetchEscrowStats()])
    orders.value = os
    stats.value = st
  } catch (e: any) {
    ElMessage.error('存管数据加载失败：' + (e?.message ?? '未知错误'))
  } finally {
    loading.value = false
  }
}
onMounted(load)

const fmtYuan = (fen?: number) => (fen == null ? '—' : '¥' + (fen / 100).toFixed(2))
const fmtTime = (t?: string) => (t ? t.replace('T', ' ').slice(0, 16) : '—')

const filtered = computed(() =>
  orders.value.filter((o) => {
    const k = keyword.value.trim()
    if (!k) return true
    return (o.memberName ?? '').includes(k) || (o.packageName ?? '').includes(k) || (o.escrowNo ?? '').includes(k) || (o.financeOrderId ?? '').includes(k)
  }),
)

const statusType = (s?: string) =>
  ({
    待托管: 'info',
    已托管: 'warning',
    放款中: 'warning',
    已放款: 'success',
    退款中: 'danger',
    已退款: 'danger',
  })[s ?? ''] ?? 'info'

const channelLabel = (c?: string) => (c === 'digital-rmb' ? '数字人民币' : c === 'bank-escrow' ? '银行存管' : c ?? '—')

/* ---------- 发起托管 ---------- */
const holdVisible = ref(false)
const holdForm = ref({ memberId: '', packageName: '高客单价套餐', amount: 0, channel: 'digital-rmb' })
const holdLoading = ref(false)
const openHold = () => {
  holdForm.value = { memberId: '', packageName: '高客单价套餐', amount: 0, channel: 'digital-rmb' }
  holdVisible.value = true
}
const submitHold = async () => {
  if (!holdForm.value.memberId.trim()) return ElMessage.warning('请填写会员 ID')
  if (!holdForm.value.amount || holdForm.value.amount <= 0) return ElMessage.warning('请填写正确的托管金额（元）')
  holdLoading.value = true
  try {
    await holdEscrow({
      memberId: holdForm.value.memberId.trim(),
      packageName: holdForm.value.packageName.trim() || '高客单价套餐',
      amount: Math.round(holdForm.value.amount * 100),
      channel: holdForm.value.channel,
    })
    ElMessage.success('托管成功，资金已进入存管')
    holdVisible.value = false
    await load()
  } catch (e: any) {
    ElMessage.error('托管失败：' + (e?.message ?? '未知错误'))
  } finally {
    holdLoading.value = false
  }
}

/* ---------- 退款 ---------- */
const refundVisible = ref(false)
const refundTarget = ref<EscrowOrder | null>(null)
const refundForm = ref({ amount: 0, reason: '' })
const refundLoading = ref(false)
const openRefund = (o: EscrowOrder) => {
  refundTarget.value = o
  refundForm.value = { amount: Math.round((o.amount ?? 0) / 100), reason: '' }
  refundVisible.value = true
}
const submitRefund = async () => {
  if (!refundTarget.value) return
  if (!refundForm.value.amount || refundForm.value.amount <= 0) return ElMessage.warning('请填写退款金额（元）')
  await ElMessageBox.confirm(
    `确认对「${refundTarget.value.memberName}」退款 ¥${refundForm.value.amount.toFixed(2)}？该操作将留痕。`,
    '退费透明确认',
    { type: 'warning' },
  ).catch(() => {
    throw new Error('已取消')
  })
  refundLoading.value = true
  try {
    await refundEscrow(refundTarget.value.id, Math.round(refundForm.value.amount * 100), refundForm.value.reason)
    ElMessage.success('退款成功，金额与原因已留痕')
    refundVisible.value = false
    await load()
  } catch (e: any) {
    if (e.message !== '已取消') ElMessage.error('退款失败：' + (e?.message ?? '未知错误'))
  } finally {
    refundLoading.value = false
  }
}

const onRelease = async (o: EscrowOrder) => {
  try {
    await releaseEscrow(o.id)
    ElMessage.success('放款成功，资金已释放给服务商')
    await load()
  } catch (e: any) {
    ElMessage.error('放款失败：' + (e?.message ?? '未知错误'))
  }
}
</script>

<template>
  <div class="escrow-page">
    <header class="escrow-head">
      <div>
        <h2>预付资金存管</h2>
        <p class="sub">高客单价套餐预付款进入存管，履约放款、争议退款，全程状态留痕（退费透明）</p>
      </div>
      <el-button type="primary" @click="openHold">发起托管</el-button>
    </header>

    <section class="stat-row" v-if="stats">
      <div class="stat-card tone-info">
        <div class="stat-label">在管金额</div>
        <div class="stat-value">{{ fmtYuan(stats.heldAmount) }}</div>
        <div class="stat-sub">在管 {{ stats.heldCount ?? 0 }} 笔</div>
      </div>
      <div class="stat-card tone-success">
        <div class="stat-label">已放款</div>
        <div class="stat-value">{{ fmtYuan(stats.releasedAmount) }}</div>
        <div class="stat-sub">已放款 {{ stats.releasedCount ?? 0 }} 笔</div>
      </div>
      <div class="stat-card tone-error">
        <div class="stat-label">已退款</div>
        <div class="stat-value">{{ fmtYuan(stats.refundedAmount) }}</div>
        <div class="stat-sub">已退款 {{ stats.refundedCount ?? 0 }} 笔</div>
      </div>
      <div class="stat-card tone-primary">
        <div class="stat-label">退款率</div>
        <div class="stat-value">{{ stats.refundRate ?? 0 }}%</div>
        <div class="stat-sub">累计 {{ stats.total ?? 0 }} 笔</div>
      </div>
    </section>

    <section class="table-block">
      <div class="table-toolbar">
        <el-input v-model="keyword" placeholder="搜索会员 / 套餐 / 存管流水号 / 关联订单" clearable style="width: 320px" />
      </div>
      <el-table :data="filtered" v-loading="loading" stripe border>
        <el-table-column prop="memberName" label="会员" min-width="110" />
        <el-table-column prop="packageName" label="套餐" min-width="140" />
        <el-table-column label="关联收款订单" min-width="150">
          <template #default="{ row }">
            <el-tag v-if="row.financeOrderId" size="small" type="primary" effect="plain">
              套餐购买 {{ row.financeOrderId }}
            </el-tag>
            <span v-else class="muted">— 手动托管</span>
          </template>
        </el-table-column>
        <el-table-column label="金额" min-width="110">
          <template #default="{ row }">{{ fmtYuan(row.amount) }}</template>
        </el-table-column>
        <el-table-column label="通道" min-width="100">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ channelLabel(row.channel) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" min-width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="escrowNo" label="存管流水号" min-width="150" />
        <el-table-column label="托管时间" min-width="140">
          <template #default="{ row }">{{ fmtTime(row.holdAt) }}</template>
        </el-table-column>
        <el-table-column label="退款" min-width="150">
          <template #default="{ row }">
            <span v-if="row.refundAmount">{{ fmtYuan(row.refundAmount) }}</span>
            <span v-else class="muted">—</span>
            <div v-if="row.refundReason" class="muted small">{{ row.refundReason }}</div>
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="170" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === '已托管' || row.status === '放款中'"
              size="small" type="success" @click="onRelease(row)"
            >放款</el-button>
            <el-button
              v-if="row.status === '已托管' || row.status === '已放款'"
              size="small" type="danger" @click="openRefund(row)"
            >退款</el-button>
            <span v-if="row.status === '已退款'" class="muted small">已退款</span>
            <span v-if="row.status === '已放款'" class="muted small">已完成</span>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <!-- 发起托管 -->
    <el-dialog v-model="holdVisible" title="发起资金托管" width="480px">
      <el-form label-width="92px">
        <el-form-item label="会员 ID" required>
          <el-input v-model="holdForm.memberId" placeholder="会员记录 ID（如 M...）" />
        </el-form-item>
        <el-form-item label="套餐名称">
          <el-input v-model="holdForm.packageName" placeholder="高客单价套餐" />
        </el-form-item>
        <el-form-item label="托管金额" required>
          <el-input-number v-model="holdForm.amount" :min="1" :precision="2" :step="100" />
          <span class="unit">元</span>
        </el-form-item>
        <el-form-item label="存管通道">
          <el-radio-group v-model="holdForm.channel">
            <el-radio value="digital-rmb">数字人民币</el-radio>
            <el-radio value="bank-escrow">银行存管</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="holdVisible = false">取消</el-button>
        <el-button type="primary" :loading="holdLoading" @click="submitHold">确认托管</el-button>
      </template>
    </el-dialog>

    <!-- 退款 -->
    <el-dialog v-model="refundVisible" title="退费（透明留痕）" width="480px">
      <el-alert
        v-if="refundTarget"
        :title="`会员：${refundTarget.memberName}　托管金额：${fmtYuan(refundTarget.amount)}`"
        type="info" :closable="false" style="margin-bottom: 14px"
      />
      <el-form label-width="92px">
        <el-form-item label="退款金额" required>
          <el-input-number v-model="refundForm.amount" :min="0.01" :precision="2" :step="100" />
          <span class="unit">元</span>
        </el-form-item>
        <el-form-item label="退款原因">
          <el-input v-model="refundForm.reason" type="textarea" :rows="3" placeholder="退费透明：记录退款原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="refundVisible = false">取消</el-button>
        <el-button type="danger" :loading="refundLoading" @click="submitRefund">确认退款</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.escrow-page { padding: 20px 24px 32px; }
.escrow-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; }
.escrow-head h2 { margin: 0; font-size: 1.25rem; font-weight: 600; color: var(--dt-ink-1); }
.escrow-head .sub { margin: 4px 0 0; color: var(--dt-ink-3); font-size: 0.8125rem; }
.stat-row { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; margin-bottom: 16px; }
.stat-card {
  background: var(--dt-card-solid);
  border: 1px solid var(--dt-border);
  border-radius: var(--dt-radius-lg);
  box-shadow: var(--dt-shadow-sm);
  padding: 16px 20px;
}
.stat-label { font-size: 0.8125rem; color: var(--dt-ink-3); }
.stat-value { font-size: 1.5rem; font-weight: 600; font-family: var(--dt-font-num); margin: 6px 0 2px; color: var(--dt-ink-1); }
.stat-card.tone-info .stat-value { color: var(--dt-info); }
.stat-card.tone-success .stat-value { color: var(--dt-success); }
.stat-card.tone-error .stat-value { color: var(--dt-error); }
.stat-card.tone-primary .stat-value { color: var(--dt-primary); }
.stat-sub { font-size: 0.75rem; color: var(--dt-ink-4); }
.table-block { background: var(--dt-card-solid); border: 1px solid var(--dt-border); border-radius: var(--dt-radius-lg); padding: 16px; }
.table-toolbar { margin-bottom: 12px; display: flex; gap: 12px; }
.unit { margin-left: 8px; color: var(--dt-ink-3); }
.muted { color: var(--dt-ink-4); }
.small { font-size: 0.75rem; }
</style>
