import { api } from '../http'

/** 红娘人效 */
export interface AgentEff {
  name: string
  monthMatch: number
  monthRevenue: number
  successRate: number
}

/** 运营成效指标（让智能运营看得见商业结果） */
export interface OpsMetrics {
  churnDays: number
  renewalDays: number
  churnCount: number
  renewalDueCount: number
  renewalRecovered30d: number
  referralCount: number
  referralRate: number
  leadTotal: number
  agentEfficiency: AgentEff[]
  updatedAt: string
}

/** 运营成效总览 */
export const fetchOpsMetrics = () => api<OpsMetrics>('/ops/metrics')

/** 转化漏斗单层级 */
export interface FunnelStage {
  name: string
  count: number
  prevCount: number | null
  /** 相对上一层转化率（比例，1=100%） */
  conversion: number
}

/** 转化获客漏斗数据（注册→认证→付费→牵线→约见→成功） */
export interface FunnelData {
  days: number
  window: string
  stages: FunnelStage[]
  overallSuccessRate: number
  overallPaidRate: number
  benchmarkSuccessRate: number
  benchmarkPaidRate: number
  agentEfficiency: AgentEff[]
  updatedAt: string
}

/** 转化获客漏斗（days 不传=全部） */
export const fetchFunnel = (days?: number) =>
  api<FunnelData>('/ops/funnel' + (days && days > 0 ? `?days=${days}` : ''))

