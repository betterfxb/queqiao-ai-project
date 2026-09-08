/**
 * 会员端（MEMBER 角色）API —— 对接阶段零·0-A 会员门户接口。
 * 复用 @/api/index 的 request / token 存储与 401 处理，仅切换基地址与端点。
 */
import { request, setToken, setStoredUser } from '@/api/index'

export interface MemberUser {
  id: string
  name: string
  role: 'MEMBER'
  gender?: string
  avatarBg?: string
  [k: string]: any
}

export const memberApi = {
  /** 会员登录：手机号 或 微信 openid */
  login: (data: { phone?: string; wechatOpenId?: string }) =>
    request<{ token: string; expiresIn: number; user: MemberUser }>({
      url: '/api/auth/member-login',
      method: 'POST',
      data,
    }),
  /** 本人资料 */
  me: () => request<any>({ url: '/api/member/me' }),
  /** 推给我的牵线（含对方画像 + 我的表态） */
  pairs: () => request<any[]>({ url: '/api/member/pairs' }),
  /** 对牵线表态：接受 / 暂不 */
  respond: (id: string, kind: 'success' | 'reject', note?: string) =>
    request<{ ok: boolean; pair: any }>({
      url: `/api/member/pairs/${id}/respond`,
      method: 'POST',
      data: { kind, note },
    }),
  /** 可报名活动（含 enrolled 标记） */
  activities: () => request<any[]>({ url: '/api/member/activities' }),
  /** 报名活动 */
  enroll: (id: string) =>
    request<{ ok: boolean; already?: boolean }>({
      url: `/api/member/activities/${id}/enroll`,
      method: 'POST',
    }),
  /** 我的认证状态总览 */
  verify: () => request<any>({ url: '/api/member/verify' }),
  /** 自动实人认证（2-A）：上传活体照 + 活体令牌，点亮「已实人」徽章 */
  realPerson: (data: { authToken?: string; liveImage?: string }) =>
    request<any>({ url: '/api/verify/real-person', method: 'POST', data }),
  /** 我的专属推荐码（2-B 全民红娘裂变） */
  myCode: () => request<{ code: string }>({ url: '/api/referral/code' }),
  /** C 端裂变：好友凭码登记为被推荐人 */
  registerByCode: (data: { code: string; refereeName?: string; refereePhone: string }) =>
    request<any>({ url: '/api/referral/register-by-code', method: 'POST', data }),
  /** 我的推荐记录 */
  myReferrals: () => request<any[]>({ url: '/api/referral/mine' }),
  /** 我的积分（含推荐成功奖励明细） */
  myPoints: () => request<any>({ url: '/api/member/points' }),
  /** 绑定家长（2-C，子女本人） */
  bindGuardian: (data: { guardianPhone: string; guardianName?: string; relation?: string }) =>
    request<any>({ url: '/api/member/guardian', method: 'POST', data }),
  /** 我的家长列表（2-C，子女本人） */
  myGuardians: () => request<any[]>({ url: '/api/member/guardian' }),
  /** 授权家长（2-C，子女本人） */
  authorizeGuardian: (id: string) =>
    request<any>({ url: `/api/member/guardian/${id}/authorize`, method: 'POST' }),
  /** 撤销家长（2-C，子女本人） */
  revokeGuardian: (id: string) =>
    request<any>({ url: `/api/member/guardian/${id}/revoke`, method: 'POST' }),
  /** 家长视角：已授权子女的会话（2-C，家长本人） */
  guardianConversations: () => request<any[]>({ url: '/api/member/guardian/conversations' }),
  /** 家长代沟通（2-C，家长本人） */
  guardianSend: (convId: string, content: string) =>
    request<any>({ url: `/api/member/guardian/conversations/${convId}/messages`, method: 'POST', data: { content } }),
}

/** 登录成功后落地 token 与用户信息（与红娘端共用 storage key） */
export function memberLoginAndStore(r: { token: string; user: MemberUser }) {
  setToken(r.token)
  setStoredUser(r.user as any)
}
