import { api } from '../http'

/** 流失预警项 */
export interface ChurnItem {
  member: Record<string, unknown>
  days: number
  feedback?: string
  reason: string
}

/** 续费提醒项 */
export interface RenewalItem {
  member: Record<string, unknown>
  expire: string
  daysLeft: number
}

/** 流失预警列表 */
export const fetchChurn = () =>
  api<{ list: ChurnItem[]; total: number }>('/insights/churn')

/** 续费提醒列表（1-G：与 finance 同源，直接引用真实订单到期日） */
export const fetchRenewal = () =>
  api<{ list: RenewalItem[]; total: number }>('/insights/renewals')

/** 1-F 预测式流失评分项（提前 14 天） */
export interface ChurnScoreItem {
  member: Record<string, unknown>
  score: number
  level: string
  predictedAt: string
  reasons: string[]
}

/** 预测式流失评分（提前 14 天）：返回中高风险会员及其评分与理由 */
export const fetchChurnScore = () =>
  api<{ list: ChurnScoreItem[]; total: number }>('/insights/churn-score')

/** 1-F 漏斗归因：掉点最多步骤 + 原因标签 */
export interface FunnelAttribution {
  stages: { name: string; count: number; prevCount: number | null; conversion: number }[]
  worstStep: string
  worstDrop: number
  reasons: string[]
}

/** 漏斗归因：定位转化掉点最多的步骤 */
export const fetchFunnelAttribution = () =>
  api<FunnelAttribution>('/insights/funnel-attribution')
