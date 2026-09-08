import { api } from '../http'

const unwrap = <T>(r: T[] | { list?: T[] }): T[] =>
  Array.isArray(r) ? r : ((r?.list ?? []) as T[])

export interface CommissionRecord {
  id: string
  agentId: string | null
  agentName: string
  orderId: string
  pairId: string | null
  type: '成功' | '续费'
  amount: number
  orderAmount: number | null
  rate: number | null
  status: '待结算' | '已结算' | '已提现'
  batchNo: string | null
  settledAt: string | null
  withdrawnAt: string | null
  createdAt: string
}

export interface CommissionSummary {
  pendingAmount: number
  settledAmount: number
  withdrawnAmount: number
  totalAmount: number
  pendingCount: number
  settledCount: number
  withdrawnCount: number
  totalCount: number
  revenueTotal?: number
  commissionRatio?: number
}

/** 红娘本人佣金 */
export const fetchMyCommission = () =>
  api<CommissionRecord[]>('/commission/mine').then(unwrap)

/** 团队佣金（主管/管理员，可按红娘姓名过滤） */
export const fetchTeamCommission = (agentName?: string) =>
  api<CommissionRecord[]>('/commission/team' + (agentName ? `?agentName=${encodeURIComponent(agentName)}` : '')).then(unwrap)

/** 聚合统计（团队维度含分佣占营收比） */
export const fetchCommissionSummary = (scope: 'mine' | 'team' = 'team', agentName?: string) =>
  api<CommissionSummary>(
    '/commission/summary?scope=' + scope + (agentName ? `&agentName=${encodeURIComponent(agentName)}` : ''),
  )

/** 生成结算单：待结算 → 已结算（管理员） */
export const settleCommission = (body: { agentName?: string; from?: string; to?: string }) =>
  api<{ batchNo: string; count: number; total: number }>('/commission/settle', { method: 'POST', body })

/** 提现记录：已结算 → 已提现（管理员） */
export const withdrawCommission = (body: { agentName?: string; from?: string; to?: string }) =>
  api<{ count: number; total: number }>('/commission/withdraw', { method: 'POST', body })
