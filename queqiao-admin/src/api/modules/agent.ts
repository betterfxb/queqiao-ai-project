import { api } from '../http'
import type { Agent } from '../types'

/** 与 member/match/finance 等模块一致：后端列表接口返回 PageResult { list, total }，统一剥出数组 */
const unwrap = (r: any): Agent[] => (Array.isArray(r) ? r : (r?.list ?? []))

export const fetchAgents = () => api<Agent[]>('/agents').then(unwrap)

export const fetchAgentStats = () =>
  api('/agents/stats')

/** 红娘详情（含名下会员 members 与牵线记录 pairs） */
export const fetchAgent = (id: string) =>
  api<{ agent: Agent; members: Record<string, unknown>[]; pairs: Record<string, unknown>[] }>('/agents/' + id)

export const fetchAgentRanking = () =>
  api('/agents/ranking')

/** AI 智能分配：body 传 {member, memberLevel, memberCity}；后端自动补齐在职红娘池 */
export const assignSuggest = (body: Record<string, unknown>) =>
  api<{
    source: string
    agent?: { id: string; name: string; level: string }
    score?: number
    reasons?: string[]
    candidates?: { agent: Agent; score: number; reason: string }[]
    list?: { agent: Agent; score: number; reason: string }[]
  }>('/agents/assign-suggest', { method: 'POST', body })

export const createAgent = (data: Record<string, unknown>) =>
  api('/agents', { method: 'POST', body: data })

export const updateAgent = (id: string, data: Record<string, unknown>) =>
  api('/agents/' + id, { method: 'PUT', body: data })

export const deleteAgent = (id: string) =>
  api('/agents/' + id, { method: 'DELETE' })
