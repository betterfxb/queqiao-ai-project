import { api } from '../http'

const unwrap = (r: any) => (Array.isArray(r) ? r : r?.list ?? [])

/** 预付资金存管订单 */
export interface EscrowOrder {
  id: string
  memberId?: string
  memberName?: string
  packageId?: string
  packageName?: string
  /** 关联的原始交易订单（FinanceOrder.id），实现「套餐购买 → 资金托管」闭环溯源 */
  financeOrderId?: string
  /** 金额（分） */
  amount?: number
  /** 币种：rmb / digital-rmb */
  currency?: string
  /** 通道：digital-rmb / bank-escrow */
  channel?: string
  /** 状态：待托管 / 已托管 / 放款中 / 已放款 / 退款中 / 已退款 */
  status?: string
  /** 通道侧存管流水号 */
  escrowNo?: string
  paidAt?: string
  holdAt?: string
  releasedAt?: string
  refundAmount?: number
  refundReason?: string
  refundAt?: string
  createdAt?: string
  updatedAt?: string
}

/** 存管透明统计 */
export interface EscrowStats {
  total?: number
  heldCount?: number
  releasedCount?: number
  refundedCount?: number
  heldAmount?: number
  releasedAmount?: number
  refundedAmount?: number
  refundRate?: number
}

/** 存管订单列表 */
export const fetchEscrows = (memberId?: string) =>
  api<EscrowOrder[]>('/escrow' + (memberId ? `?memberId=${encodeURIComponent(memberId)}` : '')).then(unwrap)

/** 存管透明统计 */
export const fetchEscrowStats = () => api<EscrowStats>('/escrow/stats')

/** 发起托管（高客单价套餐预付款进入存管） */
export const holdEscrow = (data: {
  memberId: string
  packageId?: string
  packageName?: string
  amount: number
  currency?: string
  channel?: string
}) => api<EscrowOrder>('/escrow/hold', { method: 'POST', body: data })

/** 放款：服务履约完成，释放资金给服务商 */
export const releaseEscrow = (id: string) =>
  api<EscrowOrder>('/escrow/' + id + '/release', { method: 'POST' })

/** 退款：退费透明（记录金额与原因） */
export const refundEscrow = (id: string, amount?: number, reason?: string) =>
  api<EscrowOrder>('/escrow/' + id + '/refund', {
    method: 'POST',
    body: { amount, reason },
  })
