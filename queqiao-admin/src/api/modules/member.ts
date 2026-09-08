import { api, download } from '../http'
import type { Member } from '../types'

/** 会员列表查询参数（与后端 MemberController#list 完全对齐） */
export interface MemberQuery {
  keyword?: string
  gender?: string
  level?: string
  status?: string
  agent?: string
  city?: string
  education?: string
  ageMin?: number
  ageMax?: number
  heightMin?: number
  heightMax?: number
  income?: string
  marriage?: string
  house?: string
  car?: string
  page?: number
  size?: number
  sortBy?: string
  order?: 'asc' | 'desc'
}

export interface PageResult<T> {
  list: T[]
  total: number
  page: number
  size: number
}

const unwrap = (r: any): any[] => (Array.isArray(r) ? r : (r?.list ?? []))

/** 会员列表（仅取数组） */
export const fetchMembers = (q?: MemberQuery) =>
  api<PageResult<Member>>('/members', {
    query: q as Record<string, string | number | undefined>,
  }).then(unwrap) as Promise<Member[]>

/** 会员列表（保留分页信息） */
export const fetchMemberPage = (q?: MemberQuery) =>
  api<PageResult<Member>>('/members', {
    query: q as Record<string, string | number | undefined>,
  }).then((r: any) => ({
    list: unwrap(r) as Member[],
    total: Number(r?.total ?? unwrap(r).length),
    page: Number(r?.page ?? 1),
    size: Number(r?.size ?? 20),
  }))

export const fetchMemberStats = () =>
  api<Record<string, number>>('/members/stats')

/** 详情：返回 { member, pairs } */
export const fetchMember = (id: string) =>
  api<{ member: Member; pairs: any[] }>('/members/' + id)

export const createMember = (data: Record<string, unknown>) =>
  api<Member>('/members', { method: 'POST', body: data })

export const updateMember = (id: string, data: Record<string, unknown>) =>
  api<Member>('/members/' + id, { method: 'PUT', body: data })

export const setMemberStatus = (id: string, status: string) =>
  api<Member>('/members/' + id + '/status', { method: 'POST', body: { status } })

export const assignMemberAgent = (id: string, agent: string) =>
  api<Member>('/members/' + id + '/assign', { method: 'POST', body: { agent } })

export const deleteMember = (id: string) =>
  api<{ ok: boolean }>('/members/' + id, { method: 'DELETE' })

export const batchDeleteMembers = (ids: string[]) =>
  api<{ ok: boolean; count: number }>('/members/batch-delete', { method: 'POST', body: { ids } })

/** 导出 CSV（浏览器直接下载） */
export const exportMembers = (q?: Pick<MemberQuery, 'keyword' | 'gender' | 'level' | 'status'>) => {
  const qs = Object.entries(q ?? {})
    .filter(([, v]) => v !== undefined && v !== '' && v !== '全部')
    .map(([k, v]) => `${k}=${encodeURIComponent(String(v))}`)
    .join('&')
  return download(
    '/members/export' + (qs ? '?' + qs : ''),
    `会员名册_${new Date().toISOString().slice(0, 10)}.csv`,
  )
}

/** AI 资料解析：粘贴一段文本 → 结构化会员字段 */
export const parseMemberText = (text: string) =>
  api<Record<string, unknown>>('/members/parse', { method: 'POST', body: { text } })

/* ============ 会员积分体系（1-A） ============ */

export interface PointLogItem {
  id: string
  memberId: string
  memberName: string
  type: string
  typeLabel: string
  delta: number
  refId: string | null
  operator: string | null
  remark: string | null
  createdAt: string
}

export interface MemberPoints {
  memberId: string
  total: number
  level: string
  updatedAt: string | null
  logs: PointLogItem[]
}

export interface LeaderboardRow {
  memberId: string
  memberName: string
  total: number
  level: string
}

export interface PointRule {
  type: string
  label: string
  points: number
  once: boolean
}

/** 会员积分概览（累计 / 等级 / 近 20 条流水） */
export const fetchMemberPoints = (id: string) =>
  api<MemberPoints>('/members/' + id + '/points')

/** 手动发放积分（按规则类型），返回发放结果 */
export const earnMemberPoints = (
  id: string,
  payload: { type: string; refId?: string; remark?: string },
) =>
  api<{ ok: boolean; message?: string; log?: { type: string; typeLabel: string; delta: number } }>(
    '/members/' + id + '/points/earn',
    { method: 'POST', body: payload },
  )

/** 积分排行榜（按累计积分降序） */
export const fetchPointsLeaderboard = (limit = 20) =>
  api<LeaderboardRow[]>('/points/leaderboard', { query: { limit } })

/** 积分规则说明 */
export const fetchPointRules = () =>
  api<PointRule[]>('/points/rules')
