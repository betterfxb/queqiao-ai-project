import { api } from '../http'

const unwrap = <T>(r: T[] | { list?: T[] }): T[] =>
  Array.isArray(r) ? r : ((r?.list ?? []) as T[])

/** 营销自动化规则 */
export interface AutomationRule {
  id: string
  name: string
  desc: string
  scope: string
  enabled: boolean
  lastRun?: string
  lastCreated?: number
  cron?: string
}

/** 规则清单 */
export const fetchAutomationRules = () =>
  api<AutomationRule[]>('/automation/rules').then(unwrap)

/** 启停规则 */
export const toggleAutomationRule = (id: string, enabled: boolean) =>
  api<{ id: string; enabled: boolean }>('/automation/rules/' + id + '/toggle', {
    method: 'POST',
    body: { enabled },
  })

/** 执行规则（真实写库：消息 + 日程；幂等防重复） */
export const runAutomation = (ruleId: string) =>
  api<{ ok: boolean; ruleId: string; created: number; skipped: number; detail: string[] }>(
    '/automation/run',
    { method: 'POST', body: { ruleId } },
  )

/** 1-F 预警→动作自动编排：对高流失风险会员自动生成跟进并指派红娘（幂等） */
export const applyAutomation = (memberId: string) =>
  api<{ ok: boolean; dup?: boolean; memberId: string; agent: string; type: string }>(
    '/automation/apply',
    { method: 'POST', body: { memberId } },
  )

/** 3-D 满意度闭环·接入 smart-ops：扫描近 N 天低分反馈，自动建跟进 + 红娘改进建议（幂等） */
export const scanLowSatisfaction = (days = 30) =>
  api<{ ok: boolean; scanned: number; created: number; skipped: number }>(
    '/automation/satisfaction-scan',
    { method: 'POST', body: { days } },
  )
