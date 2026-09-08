import { api } from '../http'
import type { Member } from '../types'

const unwrap = (r: any): any[] => (Array.isArray(r) ? r : (r?.list ?? []))

/** 牵线配对（与后端 Pair 实体对齐） */
export interface Pair {
  id: string
  maleId: string
  maleName: string
  femaleId: string
  femaleName: string
  score?: number
  reason?: string
  status: string
  stage: string
  agent?: string
  note?: string
  maleResponse?: string
  maleResponseKind?: string
  femaleResponse?: string
  femaleResponseKind?: string
  createdAt?: string
  updatedAt?: string
}

export interface PairQuery {
  keyword?: string
  status?: string
  stage?: string
  agent?: string
  page?: number
  size?: number
}

/** 配对列表（仅数组） */
export const fetchPairs = (q?: PairQuery) =>
  api<any>('/pairs', { query: q as Record<string, string | number | undefined> }).then(
    (r) => unwrap(r) as Pair[],
  )

/** 配对列表（含分页信息） */
export const fetchPairPage = (q?: PairQuery) =>
  api<any>('/pairs', { query: q as Record<string, string | number | undefined> }).then((r: any) => ({
    list: unwrap(r) as Pair[],
    total: Number(r?.total ?? unwrap(r).length),
  }))

/** 配对详情：{ pair, male, female, events } */
export const fetchPair = (id: string) =>
  api<{ pair: Pair; male: Member | null; female: Member | null; events: any[] }>('/pairs/' + id)

/** 智能推荐候选人：{ target, list, total } */
export const fetchRecommendations = (memberId: string, limit = 6) =>
  api<{ target: Member; list: any[]; total: number }>('/match/recommendations', {
    query: { memberId, limit },
  })

/** 两人匹配度详情（雷达 / 评分理由） */
export const fetchMatchScore = (maleId: string, femaleId: string) =>
  api<{ score: number; reasons: string[]; male: Member; female: Member }>('/match/score', {
    query: { maleId, femaleId },
  })

export const createPair = (data: { maleId: string; femaleId: string; agent?: string; note?: string }) =>
  api<Pair>('/pairs', { method: 'POST', body: data })

/** 更新状态 / 阶段 / 备注（拖拽改阶段即调用此接口） */
export const updatePair = (id: string, data: Partial<Pick<Pair, 'status' | 'stage' | 'note' | 'agent'>>) =>
  api<Pair>('/pairs/' + id, { method: 'PUT', body: data })

/** 终止牵线 */
export const deletePair = (id: string, reason?: string) =>
  api<{ ok: boolean }>('/pairs/' + id, {
    method: 'DELETE',
    query: { reason } as Record<string, string | undefined>,
  })

/** 记录一方反馈（结构化：满意度 + 卡点 + 备注） */
export const pairFeedback = (
  id: string,
  data: {
    // 后端 PairFeedback.side 为自由字符串；both 端由组件映射为 'agent'，
    // 控制器对 agent/both 等非 male/female 值仅沉淀结构化反馈（见 MatchController）。
    side: 'male' | 'female' | 'agent'
    text: string
    kind?: 'success' | 'warning' | 'error'
    satisfaction?: number
    blockers?: string[]
  },
) => api<Pair>('/pairs/' + id + '/feedback', { method: 'POST', body: data })

/** 安排约见（同时写入日程） */
export const pairMeeting = (
  id: string,
  data: { date?: string; startTime?: string; endTime?: string; place?: string; remark?: string },
) => api<{ pair: Pair; event: any }>('/pairs/' + id + '/meeting', { method: 'POST', body: data })

/** AI 牵线话术建议 */
export const matchReplySuggest = (data: Record<string, unknown>) =>
  api<{ source: string; suggestions: string[] }>('/match/reply-suggest', { method: 'POST', body: data })

export const fetchPairStats = () => api<Record<string, number>>('/pairs/stats')

/** 解释型匹配报告：契合点话术 + 建议沟通话题 */
export const fetchPairExplain = (id: string) =>
  api<{
    pairId: string
    score: number
    dims: Record<string, number>
    fitPoints: string[]
    topics: string[]
    summary: string
    semanticKeywords: string[]
    semanticFit: number
  }>('/pairs/' + id + '/explain')

/** AI 约会教练：基于牵线进展的下一步约会建议 + 话题/穿搭话术库 */
export interface CoachNextStep {
  pairId: string
  stage: string
  stageLabel: string
  nextStep: string
  actionWindow: string
  topics: string[]
  outfit: string[]
  openers: string[]
  deepenQuestions: string[]
  tips: string[]
  warnings: string[]
  source: string
  stageSource?: string
  statusSource?: string
}

export const fetchCoachNextStep = (pairId: string) =>
  api<CoachNextStep>('/ai/coach/next-step/' + pairId)
