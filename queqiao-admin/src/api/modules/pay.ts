import { api } from '../http'

export interface PayChannel {
  channel: string
  available: boolean
}

export interface PrepayResult {
  offline: boolean
  payUrl?: string
  qrcode?: string
  channel: string
  params?: Record<string, unknown>
}

export interface PayOrderResult {
  order: FinanceOrder
  prepay: PrepayResult
}

export interface FinanceOrder {
  id: string
  orderNo: string
  memberId?: string
  member?: string
  type?: string
  amount?: number
  status?: string
  channel?: string
  productId?: string
  productName?: string
  orderType?: string
  agent?: string
  tradeNo?: string
  paidAt?: string
  refundReason?: string
  remark?: string
  /** 使用的优惠券 ID（下单时抵扣） */
  couponId?: string
  /** 优惠券抵扣金额（订单 amount 已为抵扣后的实付额） */
  couponAmount?: number
  time?: string
  createdAt?: string
  updatedAt?: string
}

export interface PayReconcileItem {
  id: string
  reconcileDate?: string
  orderId?: string
  orderNo?: string
  channel?: string
  tradeNo?: string
  localAmount?: number
  channelAmount?: number
  diff?: number
  matched?: boolean
  note?: string
  createdAt?: string
}

/** 已配置支付渠道及可用性 */
export const fetchPayConfig = () =>
  api<{ channels: PayChannel[] }>('/pay/config')

/** 发起支付：创建待支付订单 + 预支付参数 */
export const createPay = (data: {
  memberId?: string
  member: string
  productId?: string
  amount?: number
  channel: string
  agent?: string
  remark?: string
  orderType?: string
  /** 抵现券 ID：后端校验归属/状态/有效期后按券面额折减订单金额 */
  couponId?: string
}) => api<PayOrderResult>('/pay/create', { method: 'POST', body: data })

/** 模拟渠道成功回调（线下确认收款） */
export const simulatePay = (id: string) =>
  api<FinanceOrder>('/pay/simulate/' + id, { method: 'POST' })

/** 对账差异查询（最近 N 天） */
export const fetchReconcile = (days = 30) =>
  api<{ list: PayReconcileItem[]; diffCount: number }>('/pay/reconcile', { query: { days } })

export interface ReconcileResult {
  date?: string
  checked: number
  matched: number
  diffCount: number
  diffs?: PayReconcileItem[]
}

/** 手动触发某日对账（管理员） */
export const runReconcile = (date?: string) =>
  api<ReconcileResult>('/pay/reconcile/run', { method: 'POST', query: date ? { date } : {} })
