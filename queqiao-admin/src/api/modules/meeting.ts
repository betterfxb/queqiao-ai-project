import { api } from '../http'

/** 视频房间状态 */
export interface MeetingRoom {
  id: string
  type: string
  hostAgentId: string
  memberA?: string | null
  memberB?: string | null
  status: string
  createdAt?: string
  closedAt?: string | null
}

/** 发起视频房间返回 */
export interface MeetingCreated {
  roomId: string
  type: string
  host: string
  memberA?: string | null
  memberB?: string | null
  status: string
  wsTopic: string
  createdAt: string
}

/** 发起视频相亲房间（红娘主持三方） */
export const createMeeting = (payload: { memberA?: string; memberB?: string }) =>
  api<MeetingCreated>('/meetings/video', { method: 'POST', body: payload })

/** 查询房间状态 */
export const getMeeting = (id: string) => api<MeetingRoom>('/meetings/' + id)

/** 结束房间 */
export const closeMeeting = (id: string) =>
  api<{ ok: boolean; roomId: string; status: string }>('/meetings/' + id + '/close', { method: 'POST' })
