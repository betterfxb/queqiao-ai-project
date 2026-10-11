<script setup lang="ts">
/**
 * 支付中心 — 真实接口
 * 订单支付：/api/pay/create（发起）→ /api/pay/simulate/{id}（线下确认收款，演示回调）或 /api/pay/notify（渠道异步）
 * 退款：/api/finance/orders/{id}/refund（幂等）
 * 对账：/api/pay/reconcile（查询差异）/api/pay/reconcile/run（手动触发，管理员）
 * 渠道配置：/api/pay/config
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createPay,
  fetchOrders,
  fetchPayConfig,
  fetchProducts,
  fetchReconcile,
  fetchMembers,
  fetchCoupons,
  fetchUsableCoupons,
  issueCoupon,
  refundOrder,
  runReconcile,
  simulatePay,
  type Coupon,
  type FinanceOrder,
  type PayChannel,
  type PayReconcileItem,
  type ServiceProductItem,
  type Member,
} from '@/api'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const isAdmin = computed(() => auth.role === 'ADMIN')
/** 抵现券钱包查询后端要求主管+，红娘不展示该页签 */
const isManager = computed(() => auth.role === 'ADMIN' || auth.role === 'MANAGER')

const orders = ref<FinanceOrder[]>([])
const channels = ref<PayChannel[]>([])
const products = ref<ServiceProductItem[]>([])
const members = ref<Member[]>([])
const loading = ref(false)
const activeTab = ref<'orders' | 'reconcile' | 'coupons'>('orders')

/* ---------- 对账 ---------- */
const reconcileList = ref<PayReconcileItem[]>([])
const reconcileLoading = ref(false)
const lastRun = ref<{ checked: number; matched: number; diffCount: number } | null>(null)

const loadOrders = async () => {
  loading.value = true
  try {
    const [o, c, p, m] = await Promise.all([
      fetchOrders() as Promise<FinanceOrder[]>,
      fetchPayConfig() as Promise<{ channels: PayChannel[] }>,
      fetchProducts() as Promise<ServiceProductItem[]>,
      fetchMembers({ size: 200 }) as Promise<Member[]>,
    ])
    orders.value = o
    channels.value = c?.channels ?? []
    products.value = (p ?? []).filter((x) => x.status !== '下架')
    members.value = m ?? []
  } catch (e: any) {
    ElMessage.error('支付中心加载失败：' + (e?.message ?? '未知错误'))
  } finally {
    loading.value = false
  }
}

const loadReconcile = async () => {
  reconcileLoading.value = true
  try {
    const r = await fetchReconcile(30)
    reconcileList.value = r?.list ?? []
  } catch (e: any) {
    ElMessage.error('对账数据加载失败：' + (e?.message ?? '未知错误'))
  } finally {
    reconcileLoading.value = false
  }
}

const onTabChange = (tab: 'orders' | 'reconcile' | 'coupons') => {
  activeTab.value = tab
  if (tab === 'reconcile' && reconcileList.value.length === 0) loadReconcile()
  if (tab === 'coupons' && couponList.value.length === 0) loadCouponWallet()
}

/* ---------- 抵现券钱包（主管可查 / 管理员可发） ---------- */
const couponList = ref<Coupon[]>([])
const couponWalletLoading = ref(false)
const couponFilter = reactive({ memberId: '', status: '' })
const issueDialog = ref(false)
const issuing = ref(false)
const issueForm = reactive({ memberId: '', memberName: '', amount: 100, days: 90 })

const loadCouponWallet = async () => {
  couponWalletLoading.value = true
  try {
    couponList.value =
      (await fetchCoupons({
        memberId: couponFilter.memberId || undefined,
        status: couponFilter.status || undefined,
      })) ?? []
  } catch (e: any) {
    ElMessage.error('优惠券加载失败：' + (e?.message ?? '未知错误'))
  } finally {
    couponWalletLoading.value = false
  }
}

const openIssue = () => {
  if (!isAdmin.value) {
    ElMessage.warning('仅管理员可发放抵现券')
    return
  }
  issueForm.memberId = ''
  issueForm.memberName = ''
  issueForm.amount = 100
  issueForm.days = 90
  issueDialog.value = true
}

const onIssueMemberChange = () => {
  const m = members.value.find((x) => x.id === issueForm.memberId)
  issueForm.memberName = m?.name ?? ''
}

const submitIssue = async () => {
  if (!issueForm.memberId) {
    ElMessage.warning('请选择发放对象')
    return
  }
  if (!issueForm.amount || issueForm.amount <= 0) {
    ElMessage.warning('券面额需大于 0')
    return
  }
  issuing.value = true
  try {
    await issueCoupon({
      memberId: issueForm.memberId,
      memberName: issueForm.memberName,
      amount: issueForm.amount,
      days: issueForm.days,
    })
    ElMessage.success(`已发放 ¥${issueForm.amount} 抵现券给 ${issueForm.memberName}`)
    issueDialog.value = false
    await loadCouponWallet()
  } catch (e: any) {
    ElMessage.error('发券失败：' + (e?.message ?? '未知错误'))
  } finally {
    issuing.value = false
  }
}

const couponStatusType = (s?: string) =>
  s === '未使用' ? 'success' : s === '已使用' ? 'info' : 'warning'
const couponSource = (c: Coupon) =>
  c.sourceType === 'REFERRAL' ? '转介绍奖励' : c.sourceType === 'MANUAL' ? '手动发放' : c.sourceType || '—'

const doRunReconcile = async () => {
  if (!isAdmin.value) {
    ElMessage.warning('仅管理员可手动触发对账')
    return
  }
  try {
    const r = await runReconcile()
    lastRun.value = { checked: r.checked, matched: r.matched, diffCount: r.diffCount }
    ElMessage.success(`对账完成：核对 ${r.checked} 笔，吻合 ${r.matched} 笔，差异 ${r.diffCount} 笔`)
    await loadReconcile()
  } catch (e: any) {
    ElMessage.error('对账失败：' + (e?.message ?? '未知错误'))
  }
}

/* ---------- 发起支付 ---------- */
const dialog = ref(false)
const submitting = ref(false)
const form = reactive({
  memberId: '',
  member: '',
  productId: '',
  amount: 0,
  channel: '线下',
  agent: '',
  remark: '',
  orderType: '新购',
  couponId: '',
})

/* ---------- 抵现券 ---------- */
const coupons = ref<Coupon[]>([])
const couponLoading = ref(false)
/** 选中券的面额（不超过订单金额，与后端 CouponService.discountFor 口径一致） */
const couponDiscount = computed(() => {
  const c = coupons.value.find((x) => x.id === form.couponId)
  if (!c) return 0
  return Math.min(c.amount ?? 0, form.amount || 0)
})
/** 抵扣后实付 */
const payableAmount = computed(() => Math.max(0, Number(((form.amount || 0) - couponDiscount.value).toFixed(2))))

const loadCoupons = async (memberId: string) => {
  coupons.value = []
  form.couponId = ''
  if (!memberId) return
  couponLoading.value = true
  try {
    const list = await fetchUsableCoupons(memberId)
    const now = Date.now()
    // 仅保留未使用且未过期的券
    coupons.value = (list ?? []).filter(
      (c) => c.status === '未使用' && (!c.expireAt || new Date(c.expireAt).getTime() > now),
    )
  } catch {
    coupons.value = [] // 无券或无权限时静默降级，不阻断下单
  } finally {
    couponLoading.value = false
  }
}

const availableChannels = computed(() => channels.value.filter((c) => c.available))

const onProductChange = () => {
  const p = products.value.find((x) => x.id === form.productId)
  if (p) form.amount = p.price ?? 0
}

const openCreate = () => {
  if (!availableChannels.value.length) {
    ElMessage.warning('暂无可用的支付渠道')
    return
  }
  form.memberId = ''
  form.member = ''
  form.productId = ''
  form.amount = 0
  form.channel = availableChannels.value[0]?.channel ?? '线下'
  form.agent = ''
  form.remark = ''
  form.orderType = '新购'
  form.couponId = ''
  coupons.value = []
  dialog.value = true
}

const onMemberChange = () => {
  const m = members.value.find((x) => x.id === form.memberId)
  form.member = m?.name ?? ''
  loadCoupons(form.memberId)
}

const submitCreate = async () => {
  if (!form.member) {
    ElMessage.warning('请选择会员')
    return
  }
  if (!form.channel) {
    ElMessage.warning('请选择支付渠道')
    return
  }
  submitting.value = true
  try {
    const res = await createPay({
      memberId: form.memberId || undefined,
      member: form.member,
      productId: form.productId || undefined,
      amount: form.amount || undefined,
      channel: form.channel,
      agent: form.agent || undefined,
      remark: form.remark || undefined,
      orderType: form.orderType,
      couponId: form.couponId || undefined,
    })
    dialog.value = false
    const prepay = res.prepay
    const paid = res.order?.amount ?? payableAmount.value
    const discTip = res.order?.couponAmount ? `（已抵扣 ¥${res.order.couponAmount}）` : ''
    if (prepay?.offline) {
      // 线下模式：提示确认收款（模拟渠道回调）
      try {
        await ElMessageBox.confirm(
          `已生成待支付订单 ${res.order.orderNo}（${form.member} 实付 ¥${paid}${discTip}）。\n线下收款后请点击「确认收款」完成支付。`,
          '发起成功',
          { confirmButtonText: '确认收款', cancelButtonText: '稍后', type: 'success' },
        )
        await simulatePay(res.order.id)
        ElMessage.success('收款成功，订单已标记为已完成')
      } catch {
        ElMessage.info('可稍后在订单列表点击「支付」确认收款')
      }
    } else {
      ElMessage.success('已生成预支付参数，请在客户端完成支付')
    }
    await loadOrders()
  } catch (e: any) {
    ElMessage.error('发起支付失败：' + (e?.message ?? '未知错误'))
  } finally {
    submitting.value = false
  }
}

const onPay = async (o: FinanceOrder) => {
  try {
    await ElMessageBox.confirm(`确认 ${o.member} 的订单 ${o.orderNo} 已收款？`, '确认收款', {
      confirmButtonText: '确认收款',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await simulatePay(o.id)
    ElMessage.success('收款成功，订单已更新为已完成')
    await loadOrders()
  } catch {
    /* 取消 */
  }
}

const onRefund = async (o: FinanceOrder) => {
  if (!isAdmin.value) {
    ElMessage.warning('仅管理员可发起退款')
    return
  }
  try {
    const { value } = await ElMessageBox.prompt('请输入退款原因', '退款', {
      confirmButtonText: '确认退款',
      cancelButtonText: '取消',
      inputType: 'textarea',
      inputValidator: (v) => (v && v.trim() ? true : '请填写退款原因'),
    })
    await refundOrder(o.id, value.trim())
    ElMessage.success('退款成功（幂等：重复退款不会产生多笔记录）')
    await loadOrders()
  } catch {
    /* 取消 */
  }
}

/* ---------- 展示 ---------- */
const statusType = (s: string) =>
  s === '已完成' ? 'success' : s === '已退款' ? 'danger' : s === '待支付' ? 'warning' : 'info'
const canPay = (s: string) => s === '待支付' || s === '处理中'
const canRefund = (s: string) => s === '已完成'
const money = (v?: number) => (v == null ? '0' : '¥' + Number(v).toLocaleString())

onMounted(loadOrders)
</script>

<template>
  <div class="pay-page" v-loading="loading">
    <div class="pay-top">
      <div class="month-pill"><app-icon name="dollar" :size="15" />支付与对账</div>
      <div style="flex: 1" />
      <button class="btn btn-gold" @click="openCreate">
        <app-icon name="plus" :size="15" />发起支付
      </button>
      <button class="btn btn-ghost" :disabled="loading" @click="loadOrders">
        <app-icon name="refresh" :size="15" />刷新
      </button>
    </div>

    <div class="tabs">
      <button :class="['tab', activeTab === 'orders' && 'active']" @click="onTabChange('orders')">订单支付</button>
      <button :class="['tab', activeTab === 'reconcile' && 'active']" @click="onTabChange('reconcile')">
        对账{{ reconcileList.length ? `（${reconcileList.length}）` : '' }}
      </button>
      <button
        v-if="isManager"
        :class="['tab', activeTab === 'coupons' && 'active']"
        @click="onTabChange('coupons')"
      >
        抵现券
      </button>
    </div>

    <!-- 订单支付 -->
    <div v-show="activeTab === 'orders'" class="glass-card order-card">
      <div v-if="orders.length" class="order-table">
        <div class="oth">
          <span>订单号</span><span>会员</span><span>服务产品</span><span>金额</span>
          <span>渠道</span><span>状态</span><span class="op">操作</span>
        </div>
        <div v-for="o in orders" :key="o.id" class="otr">
          <span class="mono">{{ o.orderNo }}</span>
          <span>{{ o.member }}</span>
          <span class="muted">{{ o.productName || o.type || '—' }}</span>
          <span class="amt">{{ money(o.amount) }}</span>
          <span>{{ o.channel || '—' }}</span>
          <span><el-tag :type="statusType(o.status || '')" size="small">{{ o.status }}</el-tag></span>
          <span class="op">
            <button v-if="canPay(o.status || '')" class="link-btn primary" @click="onPay(o)">支付</button>
            <button v-if="canRefund(o.status || '')" class="link-btn danger" @click="onRefund(o)">退款</button>
            <span v-if="!canPay(o.status || '') && !canRefund(o.status || '')" class="muted">—</span>
          </span>
        </div>
      </div>
      <div v-else class="empty">暂无订单，点击「发起支付」创建第一笔</div>
    </div>

    <!-- 对账 -->
    <div v-show="activeTab === 'reconcile'" class="glass-card recon-card" v-loading="reconcileLoading">
      <div class="recon-head">
        <div class="section-title"><span class="section-title-bar" />支付对账（最近 30 天差异）</div>
        <button class="btn btn-gold" :disabled="!isAdmin" @click="doRunReconcile">
          <app-icon name="refresh" :size="15" />立即对账
        </button>
      </div>

      <div v-if="lastRun" class="recon-summary">
        <span>核对 <b>{{ lastRun.checked }}</b> 笔</span>
        <span>吻合 <b class="ok">{{ lastRun.matched }}</b> 笔</span>
        <span>差异 <b :class="lastRun.diffCount ? 'bad' : 'ok'">{{ lastRun.diffCount }}</b> 笔</span>
      </div>

      <div v-if="reconcileList.length" class="recon-table">
        <div class="rth">
          <span>订单号</span><span>渠道</span><span>本地</span><span>渠道</span><span>差异</span><span>说明</span>
        </div>
        <div v-for="d in reconcileList" :key="d.id" class="rtr">
          <span class="mono">{{ d.orderNo }}</span>
          <span>{{ d.channel || '—' }}</span>
          <span>{{ money(d.localAmount) }}</span>
          <span>{{ money(d.channelAmount) }}</span>
          <span class="bad">{{ money(d.diff) }}</span>
          <span class="muted">{{ d.note }}</span>
        </div>
      </div>
      <div v-else class="empty ok">
        <app-icon name="check" :size="18" /> 无差异，账实相符
      </div>
    </div>

    <!-- 抵现券钱包 -->
    <div v-show="activeTab === 'coupons'" class="glass-card recon-card" v-loading="couponWalletLoading">
      <div class="recon-head">
        <div class="section-title"><span class="section-title-bar" />抵现券（转介绍发奖自动发放 / 管理员手动发放）</div>
        <div class="coupon-filter">
          <el-select v-model="couponFilter.memberId" clearable filterable placeholder="全部会员" style="width: 160px" @change="loadCouponWallet">
            <el-option v-for="m in members" :key="m.id" :label="m.name" :value="m.id" />
          </el-select>
          <el-select v-model="couponFilter.status" clearable placeholder="全部状态" style="width: 120px" @change="loadCouponWallet">
            <el-option label="未使用" value="未使用" />
            <el-option label="已使用" value="已使用" />
            <el-option label="已过期" value="已过期" />
          </el-select>
          <button class="btn btn-gold" :disabled="!isAdmin" @click="openIssue">
            <app-icon name="plus" :size="15" />发放抵现券
          </button>
        </div>
      </div>
      <el-table v-if="couponList.length" :data="couponList" stripe border size="small">
        <el-table-column prop="code" label="券码" min-width="150" />
        <el-table-column prop="memberName" label="持有会员" width="120" />
        <el-table-column label="面额" width="100">
          <template #default="{ row }">{{ money(row.amount) }}</template>
        </el-table-column>
        <el-table-column label="来源" width="110">
          <template #default="{ row }">{{ couponSource(row) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="couponStatusType(row.status)">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="有效期至" width="120">
          <template #default="{ row }">{{ String(row.expireAt || '').slice(0, 10) || '—' }}</template>
        </el-table-column>
        <el-table-column prop="orderId" label="核销订单" min-width="170" show-overflow-tooltip>
          <template #default="{ row }">{{ row.orderId || '—' }}</template>
        </el-table-column>
      </el-table>
      <div v-else class="empty">暂无抵现券记录</div>
    </div>

    <!-- 发放抵现券 -->
    <el-dialog v-model="issueDialog" title="发放抵现券" width="440px" :close-on-click-modal="false">
      <el-form label-width="90px">
        <el-form-item label="发放对象" required>
          <el-select v-model="issueForm.memberId" filterable placeholder="选择会员" style="width: 100%" @change="onIssueMemberChange">
            <el-option v-for="m in members" :key="m.id" :label="m.name" :value="m.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="券面额" required>
          <el-input-number v-model="issueForm.amount" :min="1" :precision="2" :step="50" style="width: 100%" />
        </el-form-item>
        <el-form-item label="有效天数">
          <el-input-number v-model="issueForm.days" :min="1" :step="30" style="width: 100%" />
        </el-form-item>
      </el-form>
      <div class="coupon-tip">发放后会员下单时可在「抵现券」处选择使用，支付成功即自动核销。</div>
      <template #footer>
        <el-button @click="issueDialog = false">取消</el-button>
        <el-button type="primary" :loading="issuing" @click="submitIssue">确认发放</el-button>
      </template>
    </el-dialog>

    <!-- 发起支付对话框 -->
    <el-dialog v-model="dialog" title="发起支付" width="520px" :close-on-click-modal="false">
      <el-form label-width="84px">
        <el-form-item label="会员" required>
          <el-select v-model="form.memberId" filterable placeholder="选择会员" @change="onMemberChange" style="width: 100%">
            <el-option v-for="m in members" :key="m.id" :label="m.name" :value="m.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="服务产品">
          <el-select v-model="form.productId" filterable placeholder="选择产品（自动带出金额）" @change="onProductChange" style="width: 100%">
            <el-option v-for="p in products" :key="p.id" :label="`${p.name} ¥${p.price}`" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="金额">
          <el-input-number v-model="form.amount" :min="0" :precision="2" :step="100" style="width: 100%" />
        </el-form-item>
        <el-form-item label="抵现券">
          <el-select
            v-model="form.couponId"
            clearable
            :loading="couponLoading"
            :disabled="!form.memberId"
            :placeholder="form.memberId ? (coupons.length ? '选择可用抵现券（选填）' : '该会员暂无可用券') : '请先选择会员'"
            style="width: 100%"
          >
            <el-option
              v-for="c in coupons"
              :key="c.id"
              :label="`¥${c.amount} · ${c.sourceType ?? '抵现券'}${c.expireAt ? ' · 有效期至 ' + String(c.expireAt).slice(0, 10) : ''}`"
              :value="c.id"
            />
          </el-select>
          <div v-if="couponDiscount > 0" class="coupon-tip">
            已选券抵扣 <b>¥{{ couponDiscount }}</b>，实付 <b class="pay-amt">¥{{ payableAmount }}</b>
            <span class="muted">（以后端校验结果为准）</span>
          </div>
        </el-form-item>
        <el-form-item label="支付渠道" required>
          <el-radio-group v-model="form.channel">
            <el-radio v-for="c in availableChannels" :key="c.channel" :value="c.channel">{{ c.channel }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="业务类型">
          <el-select v-model="form.orderType" style="width: 100%">
            <el-option label="新购" value="新购" />
            <el-option label="续费" value="续费" />
            <el-option label="增值" value="增值" />
          </el-select>
        </el-form-item>
        <el-form-item label="红娘">
          <el-input v-model="form.agent" placeholder="选填" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="选填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitCreate">发起支付</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.pay-page { display: flex; flex-direction: column; gap: 20px; }
.coupon-tip { margin-top: 6px; font-size: 0.8125rem; color: var(--dt-ink-2); line-height: 1.6; }
.coupon-filter { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.coupon-tip .pay-amt { color: var(--dt-error); }
.coupon-tip .muted { color: var(--dt-ink-3); margin-left: 4px; }
.pay-top { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.month-pill { display: inline-flex; align-items: center; gap: 8px; padding: 8px 16px; border-radius: var(--dt-radius-md); background: var(--dt-card-solid); border: 1px solid var(--dt-border); font-weight: 600; color: var(--dt-ink-1); font-size: 0.875rem; }
.tabs { display: flex; gap: 6px; }
.tab { padding: 8px 18px; border-radius: var(--dt-radius-md) var(--dt-radius-md) 0 0; border: 1px solid var(--dt-border); border-bottom: none; background: var(--dt-muted); color: var(--dt-ink-3); font-weight: 600; font-size: 0.875rem; cursor: pointer; }
.tab.active { background: var(--dt-card-solid); color: var(--dt-ink-1); }
.glass-card { padding: 20px; border-radius: var(--dt-radius-lg); border: 1px solid var(--dt-border); background: var(--dt-card-solid); }
.order-card, .recon-card { border-top-left-radius: 0; }
.order-table, .recon-table { display: flex; flex-direction: column; }
.oth, .otr, .rth, .rtr { display: grid; grid-template-columns: 1.4fr 1fr 1.2fr 1fr 0.9fr 1fr 1fr; gap: 10px; align-items: center; padding: 12px 4px; border-bottom: 1px solid var(--dt-border); font-size: 0.8125rem; }
.rth, .rtr { grid-template-columns: 1.4fr 0.9fr 1fr 1fr 1fr 2fr; }
.oth, .rth { font-weight: 600; color: var(--dt-ink-3); font-size: 0.75rem; }
.otr:hover, .rtr:hover { background: var(--dt-muted); }
.mono { font-family: ui-monospace, monospace; font-size: 0.75rem; color: var(--dt-ink-2); }
.muted { color: var(--dt-ink-4); font-size: 0.75rem; }
.amt { font-weight: 600; color: var(--dt-success); }
.op { display: flex; gap: 10px; }
.link-btn { background: none; border: none; cursor: pointer; font-weight: 600; font-size: 0.8125rem; padding: 0; }
.link-btn.primary { color: var(--dt-primary); }
.link-btn.danger { color: var(--dt-error); }
.empty { color: var(--dt-ink-3); font-size: 0.875rem; padding: 28px 0; text-align: center; }
.empty.ok { color: var(--dt-success); display: flex; gap: 8px; align-items: center; justify-content: center; }
.recon-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 14px; }
.recon-summary { display: flex; gap: 24px; padding: 12px 16px; border-radius: var(--dt-radius-md); background: var(--dt-bg-2); border: 1px solid var(--dt-border); margin-bottom: 12px; font-size: 0.8125rem; color: var(--dt-ink-2); }
.recon-summary b { font-size: 0.9375rem; }
.recon-summary .ok { color: var(--dt-success); }
.recon-summary .bad { color: var(--dt-error); }
.bad { color: var(--dt-error); font-weight: 600; }
@media (max-width: 1100px) {
  .oth, .otr { grid-template-columns: 1fr 1fr; }
  .rth, .rtr { grid-template-columns: 1fr 1fr; }
}
</style>
