import { api } from '../http'
import type { Activity } from '../types'

const unwrap = (r: any) => (Array.isArray(r) ? r : r?.list ?? [])

/** 活动列表 */
export const fetchActivities = () =>
  api<Activity[]>('/activities').then(unwrap)

export const fetchActivity = (id: string) =>
  api<Activity>('/activities/' + id)

export const fetchActivityStats = () =>
  api('/activities/stats')

export const createActivity = (data: Record<string, unknown>) =>
  api('/activities', { method: 'POST', body: data })

export const updateActivity = (id: string, data: Record<string, unknown>) =>
  api('/activities/' + id, { method: 'PUT', body: data })

export const deleteActivity = (id: string) =>
  api('/activities/' + id, { method: 'DELETE' })

export const setActivityStatus = (id: string, status: string) =>
  api('/activities/' + id + '/status', { method: 'POST', body: { status } })

export const fetchActivitySignups = (id: string) =>
  api('/activities/' + id + '/signups')

export const signupActivity = (id: string, data: Record<string, unknown>) =>
  api('/activities/' + id + '/signup', { method: 'POST', body: data })

export const cancelSignup = (id: string, signupId: string) =>
  api('/activities/' + id + '/signup/' + signupId, { method: 'DELETE' })

/* ===== 1-E 现场签到 + 互选 ===== */
/** 现场签到（幂等） */
export const activityCheckin = (id: string, memberId: string) =>
  api<{ ok: boolean; dup?: boolean; checkin?: any }>('/activities/' + id + '/checkin', {
    method: 'POST',
    body: { memberId },
  })

/** 签到名单 */
export const fetchActivityCheckins = (id: string) => api<any[]>('/activities/' + id + '/checkins')

/** 现场互选（a 选 b；双向互选成功返回 pairId） */
export const activityMutual = (id: string, a: string, b: string) =>
  api<{ ok: boolean; dup?: boolean; mutual: boolean; pairId?: string }>('/activities/' + id + '/mutual', {
    method: 'POST',
    body: { a, b },
  })

/** 互选名单 */
export const fetchActivityMatches = (id: string) => api<any[]>('/activities/' + id + '/matches')
