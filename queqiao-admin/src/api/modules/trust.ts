import { api } from '../http'

const unwrap = <T>(r: T[] | { list?: T[] }): T[] =>
  Array.isArray(r) ? r : ((r?.list ?? []) as T[])

// —— 5.2.1 认证标签 ——
export interface MemberVerify {
  realName: boolean
  education: boolean
  asset: boolean
  realPerson?: boolean
  realNameAt: string | null
  educationAt: string | null
  assetAt: string | null
  realPersonAt?: string | null
}
export const fetchMemberVerify = (id: string) =>
  api<MemberVerify>('/members/' + id + '/verify')

// —— 5.2.2 第三方核验 ——
export interface VerifyTask {
  id: string
  memberId: string
  memberName: string
  type: string
  channel: string
  rawProof?: string
  status: string
  result?: string
  operator?: string
  createdAt: string
  updatedAt: string
}
export const fetchVerifyTasks = (q?: { memberId?: string; type?: string; status?: string }) =>
  api<VerifyTask[]>('/verify', { query: q as Record<string, string | undefined> }).then(unwrap)
export const createVerifyTask = (body: { memberId: string; type: string; channel: string; rawProof?: string }) =>
  api<VerifyTask>('/verify', { method: 'POST', body })
export const recordVerifyResult = (id: string, body: { status: string; result?: string }) =>
  api<VerifyTask>('/verify/' + id + '/result', { method: 'POST', body })

// —— 2-A 自动实人认证（实名公安 + 人脸活体）——
export const createRealPersonVerify = (body: { memberId: string; authToken?: string; liveImage?: string }) =>
  api<VerifyTask>('/verify/real-person', { method: 'POST', body })

// —— 5.2.3 风控：举报 ——
export interface Complaint {
  id: string
  reporterId?: string
  reporterName?: string
  targetMemberId: string
  targetName?: string
  type: string
  content?: string
  status: string
  handler?: string
  handleNote?: string
  handledAt?: string
  createdAt: string
}
export const fetchComplaints = (q?: { status?: string; type?: string }) =>
  api<Complaint[]>('/complaints', { query: q as Record<string, string | undefined> }).then(unwrap)
export const createComplaint = (body: { targetMemberId: string; type: string; content?: string }) =>
  api<Complaint>('/complaints', { method: 'POST', body })
export const handleComplaint = (id: string, body: { status: string; note?: string }) =>
  api<Complaint>('/complaints/' + id + '/handle', { method: 'POST', body })

// —— 5.2.3 风控：黑名单 ——
export interface BlacklistItem {
  id: string
  memberId: string
  memberName?: string
  reason?: string
  until?: string | null
  status: string
  operator?: string
  createdAt: string
}
export const fetchBlacklist = () => api<BlacklistItem[]>('/blacklist').then(unwrap)
export const addBlacklist = (body: { memberId: string; reason?: string; until?: string }) =>
  api<BlacklistItem>('/blacklist', { method: 'POST', body })
export const unbanBlacklist = (id: string) =>
  api<BlacklistItem>('/blacklist/' + id + '/unban', { method: 'POST' })
export const checkBlacklist = (memberId: string) =>
  api<{ memberId: string; blacklisted: boolean }>('/blacklist/check', { query: { memberId } })

// —— 5.2.3 风控：风险事件 ——
export interface RiskEvent {
  id: string
  memberId: string
  memberName?: string
  type: string
  score: number
  detail?: string
  /** 被害人救助引导文案（反诈类风险事件） */
  guidance?: string
  handled: boolean
  handler?: string
  handledAt?: string
  createdAt: string
}
export const scanRisk = () => api<{ scanned: number; created: number }>('/risk/scan', { method: 'POST' })
export const fetchRiskEvents = (q?: { handled?: boolean; type?: string; memberId?: string }) =>
  api<RiskEvent[]>('/risk/events', { query: q as Record<string, string | undefined> }).then(unwrap)
export const handleRisk = (id: string, body?: { note?: string }) =>
  api<RiskEvent>('/risk/events/' + id + '/handle', { method: 'POST', body })
export const fetchRiskSummary = () =>
  api<{ total: number; unhandled: number; byType: Record<string, number> }>('/risk/summary')

// —— 3-C 反诈工单 ——
export interface WorkOrder {
  id: string
  title: string
  memberId: string
  memberName?: string
  type: string
  riskType: string
  status: string
  priority: string
  relatedRiskId?: string
  guidance?: string
  detail?: string
  assignee?: string
  handler?: string
  handleNote?: string
  handledAt?: string
  createdAt: string
}
export const fetchWorkOrders = (q?: { status?: string; riskType?: string }) =>
  api<WorkOrder[]>('/workorders', { query: q as Record<string, string | undefined> }).then(unwrap)
export const handleWorkOrder = (id: string, body?: { status?: string; note?: string }) =>
  api<WorkOrder>('/workorders/' + id + '/handle', { method: 'POST', body })
export const fetchWorkOrderSummary = () =>
  api<{ total: number; open: number; byRiskType: Record<string, number> }>('/workorders/summary')

// —— 5.2.4 合规文档 ——
export interface DocTemplate {
  id: string
  type: string
  title: string
  content: string
  version: string
  updatedBy?: string
  updatedAt?: string
}
export interface ConsentRecord {
  id: string
  memberId: string
  memberName?: string
  docType: string
  version: string
  channel: string
  acceptedAt: string
}
export const fetchDocs = () => api<DocTemplate[]>('/docs').then(unwrap)
export const fetchDoc = (type: string) => api<DocTemplate>('/docs/' + type)
export const saveDoc = (type: string, body: { title: string; content: string }) =>
  api<DocTemplate>('/docs/' + type, { method: 'PUT', body })
export const consentDoc = (body: { memberId: string; memberName?: string; docType: string; version?: string; channel: string }) =>
  api<{ ok: boolean; record: ConsentRecord; acceptedAll: boolean }>('/docs/consent', { method: 'POST', body })
export const fetchConsents = (memberId: string) =>
  api<ConsentRecord[]>('/docs/consent/' + memberId).then(unwrap)
