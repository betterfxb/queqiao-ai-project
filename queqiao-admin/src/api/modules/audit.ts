import { api } from '../http'
import type { AuditItem } from '../types'
import { timeAgo } from '../../utils/format'

export interface AuditStats {
  pending: number
  urgent: number
  approved: number
  rejected: number
  byType: Record<string, number>
}

/** 审核队列（后端返回 {list,...}，统一拆包 + 字段归一化） */
export const fetchAudits = (q?: { type?: string; urgency?: string; status?: string; keyword?: string }) =>
  api<{ list: any[]; total: number }>('/audits', {
    query: q as Record<string, string | undefined>,
  }).then((r: any) => (Array.isArray(r) ? r : r.list ?? []).map(normalizeAudit))

export const fetchAuditStats = () =>
  api<AuditStats>('/audits/stats')

export const approveAudit = (id: string, remark?: string) =>
  api('/audits/' + id + '/approve', {
    method: 'POST',
    body: remark ? { remark } : {},
  })

export const rejectAudit = (id: string, reason: string) =>
  api('/audits/' + id + '/reject', { method: 'POST', body: { reason } })

export const batchApproveAudit = (ids: string[]) =>
  api('/audits/batch-approve', { method: 'POST', body: { ids } })

export const aiSuggestAudit = (id: string) =>
  api<{ source: string; decision?: string; label?: string; confidence?: number; reasons?: string[]; risks?: string[]; advice?: string }>(
    '/audits/' + id + '/ai-suggest',
    { method: 'POST' },
  )

function normalizeAudit(a: any): AuditItem {
  const change = a.change ?? (a.changeField ? { field: a.changeField, from: a.changeFrom, to: a.changeTo } : undefined)
  const pay = a.pay ?? (a.payText ? { text: a.payText, paid: !!a.paid } : undefined)
  return { ...a, timeAgo: a.timeAgo ?? timeAgo(a.submittedAt), change, pay } as AuditItem
}
