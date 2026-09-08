import { api } from '../http'

const unwrap = <T>(r: T[] | { list?: T[] }): T[] =>
  Array.isArray(r) ? r : ((r?.list ?? []) as T[])

/* =========================================================================
 * 优惠券（抵现券）
 * - 转介绍发奖时由后端 ReferralService 自动发券；
 * - 管理员可手动发放；下单时通过 /api/pay/create 的 couponId 抵扣。
 * 权限：查询=主管+，发放=管理员。
 * ========================================================================= */

export interface Coupon {
  id: string
  code?: string | null
  memberId?: string | null
  memberName?: string | null
  amount: number
  /** 券类型，目前为「抵现券」 */
  type?: string | null
  status: '未使用' | '已使用' | '已过期'
  /** 来源类型：转介绍奖励 / 手动发放 */
  sourceType?: string | null
  sourceId?: string | null
  orderId?: string | null
  expireAt?: string | null
  usedAt?: string | null
  createdAt?: string
  updatedAt?: string
}

/** 优惠券钱包：按会员 / 状态查询（主管+） */
export const fetchCoupons = (params?: { memberId?: string; status?: string }) =>
  api<Coupon[]>('/coupons', { query: { memberId: params?.memberId, status: params?.status } }).then(unwrap)

/** 某会员可用（未使用且未过期）券 */
export const fetchUsableCoupons = (memberId: string) =>
  api<Coupon[]>('/coupons', { query: { memberId, status: '未使用' } }).then(unwrap)

/** 管理员手动发放抵现券 */
export const issueCoupon = (body: {
  memberId: string
  memberName?: string
  amount: number
  days?: number
}) => api<Coupon>('/coupons/issue', { method: 'POST', body })
