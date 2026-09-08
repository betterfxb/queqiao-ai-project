import { api } from '../http'
import type { CrmCard } from '../types'

const unwrap = <T>(r: T[] | { list?: T[] }): T[] =>
  Array.isArray(r) ? r : ((r?.list ?? []) as T[])

/** CRM 看板（六列）。后端返回 [{key,title,count,cards}]，与前端结构一致。 */
export const fetchCrmBoard = (q?: { agent?: string; keyword?: string }) =>
  api<{ key: string; title: string; count: number; cards: CrmCard[] }[]>('/crm/board', {
    query: q as Record<string, string | undefined>,
  }).then(unwrap)

export const fetchCrmStats = () =>
  api<{ total: number; deal: number; convRate: number; risk: number; star: number; byStage: Record<string, number> }>(
    '/crm/stats',
  )

export const createLead = (data: Record<string, unknown>) =>
  api('/crm/leads', { method: 'POST', body: data })

export const updateLead = (id: string, data: Record<string, unknown>) =>
  api('/crm/leads/' + id, { method: 'PUT', body: data })

/** 拖拽换列：stage 传标准中文阶段（如「匹配推荐」） */
export const moveLeadStage = (id: string, stage: string) =>
  api('/crm/leads/' + id + '/stage', { method: 'POST', body: { stage } })

export const toggleLeadStar = (id: string, star?: boolean) =>
  api('/crm/leads/' + id + '/star', { method: 'POST', body: star === undefined ? {} : { star } })

export const deleteLead = (id: string) =>
  api('/crm/leads/' + id, { method: 'DELETE' })

export const addLeadFollow = (id: string, content: string, type = '电话') =>
  api('/crm/leads/' + id + '/follows', { method: 'POST', body: { content, type } })

export const fetchLeadFollows = (id: string) =>
  api('/crm/leads/' + id + '/follows')

export const nextActionLead = (id: string) =>
  api<{ source: string; action: string; reason: string; priority: string }>(
    '/crm/leads/' + id + '/next-action',
    { method: 'POST' },
  )
