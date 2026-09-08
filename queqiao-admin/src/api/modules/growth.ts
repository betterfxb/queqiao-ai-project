import { api } from '../http'

const unwrap = <T>(r: T[] | { list?: T[] }): T[] =>
  Array.isArray(r) ? r : ((r?.list ?? []) as T[])

/* =========================================================================
 * 增长与差异化（#111）— 转介绍激励 / 成功案例营销 / 同城运营 / 产业链导流 / 渠道归因
 * 后端 :8080 真实接口，无 mock 降级（增长数据必须真实可运营）。
 * 权限：ADMIN=管理员 / MANAGER=主管(ADMIN或MANAGER) / AGENT=红娘
 * ========================================================================= */

/* ---------------- 5.3.1 转介绍激励 ---------------- */
export interface Referral {
  id: string
  referrerId: string | null
  referrerName: string | null
  referrerType: string | null
  refereeName: string | null
  refereePhone: string | null
  refereeMemberId?: string | null
  refereeLeadId?: string | null
  status: '已登记' | '已转化' | '已发奖' | '已作废'
  orderAmount?: number
  rewardAmount?: number
  remark?: string | null
  createdAt?: string
}

export const fetchReferrals = (referrerId?: string) =>
  api<Referral[]>('/referral' + (referrerId ? `?referrerId=${encodeURIComponent(referrerId)}` : '')).then(unwrap)

export const fetchMyReferrals = () => api<Referral[]>('/referral/mine').then(unwrap)

export const registerReferral = (body: {
  referrerId?: string | null
  referrerType?: string
  refereeName: string
  refereePhone: string
  remark?: string
  channel?: string
}) => api<Referral>('/referral', { method: 'POST', body })

export const convertReferral = (id: string, body: { orderAmount?: number }) =>
  api<Referral>(`/referral/${id}/convert`, { method: 'POST', body })

export const rewardReferral = (id: string) =>
  api<Referral>(`/referral/${id}/reward`, { method: 'POST' })

export const invalidReferral = (id: string) =>
  api<Referral>(`/referral/${id}/invalid`, { method: 'POST' })

export const fetchReferralSummary = () =>
  api<{ total: number; registered?: number; converted?: number; rewarded?: number; paidAmount?: number }>(
    '/referral/summary',
  )

/* ---------------- 5.3.2 成功案例营销 ---------------- */
export interface Story {
  id: string
  pairId?: string | null
  maleAlias?: string | null
  femaleAlias?: string | null
  storyText?: string | null
  status: '草稿' | '已发布' | '已下架'
  publicVisible?: boolean
  createdAt?: string
}

export const fetchStories = (status?: string) =>
  api<Story[]>('/stories' + (status ? `?status=${encodeURIComponent(status)}` : '')).then(unwrap)

export const fetchPublicStories = () => api<Story[]>('/stories/public').then(unwrap)

export const draftStory = (body: { pairId: string }) =>
  api<Story>('/stories/draft', { method: 'POST', body })

export const publishStory = (id: string, body: { publicVisible?: boolean }) =>
  api<Story>(`/stories/${id}/publish`, { method: 'POST', body })

export const offlineStory = (id: string) => api<Story>(`/stories/${id}/offline`, { method: 'POST' })

export const fetchStorySummary = () =>
  api<{ total: number; draft?: number; published?: number }>('/stories/summary')

/* ---------------- 5.3.3 同城运营 ---------------- */
export interface CityPartner {
  id: string
  city: string
  ownerAgentName?: string | null
  tier: string
  status?: string
  shareRate?: number
  startDate?: string | null
  createdAt?: string
}

export interface CommunityTier {
  id: string
  city: string
  level: string
  price: number
  capacity?: number
  memberCount?: number
  benefits?: string | null
  productId?: string | null
  /** 在售 / 已下架，仅「在售」可订阅 */
  status?: string
  sortOrder?: number
  createdAt?: string
}

/** 社群档位会员（订阅支付成功后由后端自动开通） */
export interface CommunityMembership {
  id: string
  tierId: string
  tierLevel?: string
  city?: string
  memberId?: string | null
  memberName?: string | null
  /** 开通来源订单 */
  orderId?: string | null
  status: '有效' | '已退订'
  activeFrom?: string
  activeTo?: string
  createdAt?: string
}

export interface CityOverview {
  city: string
  tiers: CommunityTier[]
  partners: CityPartner[]
  communityRevenue: number
  partnerRevenue?: number
  /** 有效社群会员数 */
  communityMembersActive?: number
}

export const fetchCityPartners = () => api<CityPartner[]>('/city/partners').then(unwrap)
export const fetchCityTiers = () => api<CommunityTier[]>('/city/tiers').then(unwrap)
export const createCityTier = (body: Partial<CommunityTier>) =>
  api<CommunityTier>('/city/tiers', { method: 'POST', body })
export const updateCityTier = (id: string, body: Partial<CommunityTier>) =>
  api<CommunityTier>(`/city/tiers/${id}`, { method: 'PUT', body })
export const deleteCityTier = (id: string) => api<{ ok: boolean }>(`/city/tiers/${id}`, { method: 'DELETE' })

/**
 * 社群档位订阅下单：生成「社群订阅」待支付订单（productId 弱关联档位）。
 * 支付完成后（/api/pay/simulate 或渠道回调）后端自动开通会员并累加档位人数。
 */
export const subscribeCityTier = (tierId: string, body: { memberId?: string; memberName: string }) =>
  api<{ order: import('./pay').FinanceOrder }>(`/city/tiers/${tierId}/subscribe`, { method: 'POST', body })

/** 某档位的会员名单 */
export const fetchTierMemberships = (tierId: string) =>
  api<CommunityMembership[]>(`/city/tiers/${tierId}/memberships`).then(unwrap)

/** 全量社群会员名单 */
export const fetchCommunityMemberships = () => api<CommunityMembership[]>('/city/memberships').then(unwrap)

export const fetchCityOverview = (city?: string) =>
  api<CityOverview>('/city/overview' + (city ? `?city=${encodeURIComponent(city)}` : ''))
export const fetchCityList = () =>
  api<Array<{ city: string; tierCount?: number; partnerCount?: number }>>('/city/list').then(unwrap)

export const createCityPartner = (body: Partial<CityPartner>) =>
  api<CityPartner>('/city/partners', { method: 'POST', body })
export const updateCityPartner = (id: string, body: Partial<CityPartner>) =>
  api<CityPartner>(`/city/partners/${id}`, { method: 'PUT', body })
export const deleteCityPartner = (id: string) =>
  api<{ ok: boolean }>(`/city/partners/${id}`, { method: 'DELETE' })

/* ---------------- 5.3.4 产业链导流 ---------------- */
export interface PartnerMerchant {
  id: string
  name: string
  category?: string
  city?: string
  commissionRate: number
  status?: string
  contact?: string | null
  createdAt?: string
}

export interface MerchantOrder {
  id: string
  merchantId: string
  merchantName?: string
  memberId?: string | null
  memberName?: string | null
  amount: number
  status: '已导流' | '已成交' | '已结算' | '已取消'
  commissionRate?: number
  commissionAmount?: number
  note?: string | null
  createdAt?: string
}

export const fetchMerchants = () => api<PartnerMerchant[]>('/partners/merchants').then(unwrap)
export const createMerchant = (body: Partial<PartnerMerchant>) =>
  api<PartnerMerchant>('/partners/merchants', { method: 'POST', body })
export const updateMerchant = (id: string, body: Partial<PartnerMerchant>) =>
  api<PartnerMerchant>(`/partners/merchants/${id}`, { method: 'PUT', body })
export const deleteMerchant = (id: string) =>
  api<{ ok: boolean }>(`/partners/merchants/${id}`, { method: 'DELETE' })

export const fetchPartnerOrders = (status?: string) =>
  api<MerchantOrder[]>('/partners/orders' + (status ? `?status=${encodeURIComponent(status)}` : '')).then(unwrap)
export const createPartnerOrder = (body: { merchantId: string; memberId?: string | null; amount?: number; note?: string }) =>
  api<MerchantOrder>('/partners/orders', { method: 'POST', body })
export const dealPartnerOrder = (id: string, body: { amount: number }) =>
  api<MerchantOrder>(`/partners/orders/${id}/deal`, { method: 'POST', body })
export const settlePartnerOrder = (id: string) =>
  api<MerchantOrder>(`/partners/orders/${id}/settle`, { method: 'POST' })
export const cancelPartnerOrder = (id: string, body: { reason: string }) =>
  api<MerchantOrder>(`/partners/orders/${id}/cancel`, { method: 'POST', body })

export const recommendMerchants = (pairId: string) =>
  api<{ merchants: PartnerMerchant[] }>(`/partners/recommend?pairId=${encodeURIComponent(pairId)}`)
export const fetchPartnerSummary = () =>
  api<{ orderCount?: number; dealCount?: number; settledAmount?: number }>('/partners/summary')

/* ---------------- 5.3.5 渠道归因 ROI ---------------- */
export interface MarketChannel {
  id: string
  name: string
  category?: string
  code?: string
  cost: number
  owner?: string
  status?: string
  createdAt?: string
}

export interface ChannelRoiRow {
  name: string
  leadCount: number
  cost: number
  dealCount: number
  cac: number
  roi: number
  convRate: number
}

export interface ChannelRoi {
  leadTotal: number
  dealTotal?: number
  rows: ChannelRoiRow[]
}

export const fetchChannels = () => api<MarketChannel[]>('/channels').then(unwrap)
export const createChannel = (body: Partial<MarketChannel>) =>
  api<MarketChannel>('/channels', { method: 'POST', body })
export const updateChannel = (id: string, body: Partial<MarketChannel>) =>
  api<MarketChannel>(`/channels/${id}`, { method: 'PUT', body })
export const deleteChannel = (id: string) => api<{ ok: boolean }>(`/channels/${id}`, { method: 'DELETE' })

export const fetchChannelRoi = () => api<ChannelRoi>('/channels/roi')
export const fetchChannelRoiByOps = () => api<ChannelRoi>('/ops/channel-roi')

/* ---------------- 运营总览（增长指标合入） ---------------- */
export interface GrowthMetrics {
  referralProgram?: Record<string, unknown>
  partnerProgram?: Record<string, unknown>
  [k: string]: unknown
}

export const fetchGrowthMetrics = () => api<GrowthMetrics>('/ops/metrics')
