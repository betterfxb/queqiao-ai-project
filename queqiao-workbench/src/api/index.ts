import { buildSignHeaders } from '@/security/sign'

/**
 * API 层 — 统一封装 uni.request，对接 Spring Boot 业务后端（:8080）
 *
 * 约定（与后端契约对齐，见各控制器 javadoc）：
 *  - 登录态：JWT 存 storage「queqiao_token」，请求自动带 Authorization: Bearer；
 *    收到 401 自动清 token 并跳转登录页。
 *  - 分页：后端返回 PageResult { list, total, page, size }。
 *  - 列表裸数组：/api/calendar/events、/api/conversations 直接返回数组。
 *  - 错误：后端异常统一为 { error: string }。
 */

// API 基地址：
//  - 优先用 VITE_API_BASE 注入的绝对地址（真机/小程序/独立域名部署，务必 HTTPS）；
//  - 未注入时一律走同源相对路径（''），由 nginx 反代 /api，与《部署手册》"build 后无需改代码"一致。
//  ⚠️ 旧逻辑非 DEV 时回退 'http://localhost:8080' 是错误默认值——线上会让所有请求打到用户本地 8080 而全失败，已删除。
const BASE_URL = (import.meta.env.VITE_API_BASE as string | undefined) || ''
const TOKEN_KEY = 'queqiao_token'
const USER_KEY = 'queqiao_user'

/**
 * 手机号脱敏展示：13812341234 -> 138****1234
 * 仅用于展示，不参与入库；入库始终保存全号（见 member/input.vue）。
 */
export function maskPhone(phone?: string | null): string {
  if (!phone) return ''
  const p = String(phone).replace(/\D/g, '')
  if (p.length < 7) return p
  return `${p.slice(0, 3)}****${p.slice(-4)}`
}

export function getToken(): string {
  return uni.getStorageSync(TOKEN_KEY) || ''
}
export function setToken(token: string) {
  uni.setStorageSync(TOKEN_KEY, token)
}
export function clearAuth() {
  uni.removeStorageSync(TOKEN_KEY)
  uni.removeStorageSync(USER_KEY)
}
export function getStoredUser(): User | null {
  const u = uni.getStorageSync(USER_KEY)
  return u && u.id ? u : null
}
export function setStoredUser(u: User) {
  uni.setStorageSync(USER_KEY, u)
}

let redirecting = false
function gotoLogin() {
  if (redirecting) return
  redirecting = true
  uni.removeStorageSync(TOKEN_KEY)
  uni.reLaunch({ url: '/pages/login/index' })
  setTimeout(() => (redirecting = false), 1200)
}

export interface ApiError {
  code: number
  message: string
}

/**
 * 可取消的请求 Promise：在 Promise 上附加 abort()，用于组件卸载 / 路由跳转时取消在途请求。
 * 与管理端 http.ts 的 AbortController 能力对齐（UniApp 无 signal，改用 requestTask.abort() 实现）。
 */
export interface CancelablePromise<T> extends Promise<T> {
  abort: () => void
}

/** 取消错误：code = -999，便于调用方区分「用户取消」与「网络异常」 */
function makeAbortError(): ApiError {
  return { code: -999, message: '请求已取消' }
}

/** 请求超时（毫秒）：后端挂起时不再无限等待（与管理端 http.ts 对齐） */
const TIMEOUT_MS = 15_000
/** 网络/超时失败的一次性重试（HTTP 状态错误不重试，交由上层处理） */
const DEFAULT_RETRY = 1

export function request<T = any>(options: {
  url: string
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE'
  data?: any
  /** 传入 AbortSignal 即可在外部取消，与管理端 AbortController 用法对齐 */
  signal?: AbortSignal
  /** 网络/超时失败的重试次数，默认 1（POST 同样重试，后端对关键写操作已置幂等键兜底） */
  retry?: number
}): CancelablePromise<T> {
  const maxRetry = options.retry ?? DEFAULT_RETRY
  let requestTask: UniApp.RequestTask | null = null
  let aborted = false
  const externalSignal = options.signal
  const token = getToken()

  const p = new Promise<T>((resolve, reject) => {
    const run = (attempt: number) => {
      if (aborted) {
        reject(makeAbortError())
        return
      }
      requestTask = uni.request({
        url: BASE_URL + options.url,
        method: options.method || 'GET',
        data: options.data || {},
        timeout: TIMEOUT_MS,
        header: {
          'Content-Type': 'application/json',
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
          // 请求签名：与后端 SecurityFilter 对齐（prod 强制校验，缺签名会 401）
          ...buildSignHeaders(options.method || 'GET', (options.url || '').split('?')[0]),
        },
        success: (res) => {
          if (aborted) return
          if (res.statusCode >= 200 && res.statusCode < 300) {
            resolve(res.data as T)
          } else if (res.statusCode === 401) {
            gotoLogin()
            reject({ code: 401, message: '登录已过期，请重新登录' } as ApiError)
          } else {
            const body = res.data as any
            reject({
              code: res.statusCode,
              message: (body && (body.error || body.message)) || `请求失败(${res.statusCode})`,
            } as ApiError)
          }
        },
        fail: (err) => {
          // 外部/内部取消
          if (aborted) {
            reject(makeAbortError())
            return
          }
          const msg = (err && (err as any).errMsg ? String((err as any).errMsg) : '') as string
          if (msg.includes('abort')) {
            reject(makeAbortError())
            return
          }
          // 网络/超时错误：可重试；已达上限则如实失败
          if (attempt < maxRetry) {
            run(attempt + 1)
            return
          }
          reject({ code: -1, message: '网络异常，请检查后端服务是否启动' } as ApiError)
        },
      })
    }
    run(0)
  }) as CancelablePromise<T>

  // 暴露取消能力（组件卸载时调用 req.abort() 即可取消在途请求）
  p.abort = () => {
    aborted = true
    try {
      requestTask?.abort()
    } catch {
      /* 部分平台 requestTask 不支持 abort，忽略 */
    }
  }
  // 外部 signal 联动（与管理端 AbortController 用法对齐）
  if (externalSignal) {
    if (externalSignal.aborted) p.abort()
    else externalSignal.addEventListener('abort', () => p.abort())
  }
  return p
}

/* ---------- 类型（与后端实体/响应一致） ---------- */

export interface User {
  id: string
  username: string
  name: string
  role: 'ADMIN' | 'AGENT' | 'MANAGER'
  avatar?: string
  avatarBg?: string
  phone?: string
  email?: string
  agentId?: string
  lastLoginAt?: string
  [k: string]: any
}

export interface Member {
  id: string
  name: string
  gender?: '男' | '女'
  age?: number
  city?: string
  /** 省份（采集页省市级联，用于按省筛选） */
  province?: string
  /** 区/县（采集页三级级联末级） */
  district?: string
  education?: string
  job?: string
  level?: string
  status?: string
  agent?: string
  /** 本人照片：采集页上传，前端压缩后的 base64 dataURL（选填，可能很长，仅详情接口下发） */
  photo?: string
  /** 列表缩略图：后端按长边缩放后的小图（约 1KB），列表接口下发，避免原图拖垮列表 */
  photoThumb?: string
  matchCount?: number
  successRate?: number
  lastActive?: string
  avatarBg?: string
  phone?: string
  height?: number
  income?: string
  marriage?: string
  hasChild?: string
  hometown?: string
  house?: string
  car?: string
  requirement?: string
  tags?: string
  bio?: string
  verified?: boolean
  lastFollowUp?: string
  expire?: string
  feedback?: string
  createdAt?: string
  updatedAt?: string
  [k: string]: any
}

export interface Pair {
  id: string
  maleId?: string
  maleName?: string
  femaleId?: string
  femaleName?: string
  score?: number
  status?: string
  stage?: string
  agent?: string
  maleResponse?: string
  maleResponseKind?: string
  femaleResponse?: string
  femaleResponseKind?: string
  note?: string
  reason?: string
  createdAt?: string
  updatedAt?: string
  [k: string]: any
}

export interface FollowUp {
  id: string
  memberId?: string
  type?: string
  content?: string
  outcome?: string
  nextFollowUp?: string
  agent?: string
  createdAt?: string
  timeAgo?: string
  [k: string]: any
}

export interface FollowUpSummary {
  source?: string
  summary: string
  count: number
  latestType: string
  latestOutcome: string
  intention: string
  nextFollowUp?: string
  suggestion: string
  reminders: string[]
  [k: string]: any
}

export interface CalendarEvent {
  id: string
  title: string
  type?: string
  date?: string
  startTime?: string
  endTime?: string
  time?: string
  place?: string
  agent?: string
  status?: string
  members?: string[]
  memberIds?: string[]
  remark?: string
  pairId?: string
  [k: string]: any
}

export interface Conversation {
  id: string
  name: string
  timeAgo: string
  preview: string
  unread: number
  kind: 'member' | 'ai' | 'system' | 'group'
  online?: boolean
  avatarBg?: string
  [k: string]: any
}

export interface MessageItem {
  id?: string
  conversationId?: string
  sender: 'agent' | 'member' | 'system'
  content: string
  type?: string
  readFlag?: boolean
  createdAt?: string
  [k: string]: any
}

export interface PageResult<T> {
  list: T[]
  total: number
  page: number
  size: number
}

export interface Dashboard {
  kpis: {
    label: string
    value: string
    unit: string
    trend: string
    up: boolean
    icon: string
    tone: string
  }[]
  recentMembers: Member[]
  kanban: { title: string; count: number; cards: any[] }[]
  todos: { text: string; priority: string; link: string }[]
  recs: {
    name: string
    score: number
    detail: string
    reason: string
    avatarBg: string
    initial: string
    memberId: string
    forMemberId: string
    forMemberName: string
  }[]
}

export interface MatchRec {
  member: Member
  score: number
  reasons: string[]
  dimensions: Record<string, number>
  source: string
}

/** AI 文本解析结果（与 /api/members/parse 契约对齐） */
export interface AiParseResult {
  fields: Record<string, any>
  entity_tags?: { tag: string; adopted: boolean }[]
  field_count?: number
  source?: string
  confidence?: number
}

/* ---------- 接口 ---------- */

export const api = {
  // —— 认证 ——
  login: (username: string, password: string) =>
    request<{ token: string; expiresIn: number; user: User }>({
      url: '/api/auth/login',
      method: 'POST',
      data: { username, password },
    }),
  me: () => request<User>({ url: '/api/auth/me' }),
  logout: () => request<{ ok: boolean }>({ url: '/api/auth/logout', method: 'POST' }),
  updateProfile: (data: { name?: string; phone?: string; email?: string }) =>
    request<User>({ url: '/api/auth/profile', method: 'PUT', data }),
  changePassword: (data: { oldPassword: string; newPassword: string }) =>
    request<{ ok: boolean; message: string }>({ url: '/api/auth/change-password', method: 'POST', data }),

  // —— 工作台 ——
  dashboard: () => request<Dashboard>({ url: '/api/dashboard' }),

  // —— 会员 ——
  members: (params?: Record<string, any>) =>
    request<PageResult<Member>>({ url: '/api/members', data: params }),
  memberDetail: (id: string) =>
    request<{ member: Member; pairs: Pair[]; followups: FollowUp[] }>({ url: `/api/members/${id}` }),
  createMember: (data: Partial<Member>) => request<Member>({ url: '/api/members', method: 'POST', data }),
  updateMember: (id: string, data: Partial<Member>) =>
    request<Member>({ url: `/api/members/${id}`, method: 'PUT', data }),
  memberStats: () => request<Record<string, any>>({ url: '/api/members/stats' }),
  aiParse: (text: string) =>
    request<AiParseResult>({ url: '/api/members/parse', method: 'POST', data: { text } }),
  memberFollowups: (id: string) => request<FollowUp[]>({ url: `/api/members/${id}/followups` }),
  createFollowup: (id: string, data: { type?: string; content: string; outcome?: string; nextFollowUp?: string }) =>
    request<FollowUp>({ url: `/api/members/${id}/followups`, method: 'POST', data }),
  followupSummary: (id: string) => request<FollowUpSummary>({ url: `/api/members/${id}/followups/summary` }),

  // —— 牵线 ——
  pairs: (params?: Record<string, any>) => request<PageResult<Pair>>({ url: '/api/pairs', data: params }),
  pairDetail: (id: string) =>
    request<{ pair: Pair; male?: Member; female?: Member; events: CalendarEvent[] }>({ url: `/api/pairs/${id}` }),
  createPair: (data: { maleId: string; femaleId: string; note?: string }) =>
    request<Pair>({ url: '/api/pairs', method: 'POST', data }),
  pairStats: () => request<Record<string, any>>({ url: '/api/pairs/stats' }),
  pairFeedback: (id: string, data: { side: string; text: string; kind: string }) =>
    request<Pair>({ url: `/api/pairs/${id}/feedback`, method: 'POST', data }),
  pairMeeting: (id: string, data: { date?: string; startTime?: string; endTime?: string; place?: string; remark?: string }) =>
    request<{ pair: Pair; event: CalendarEvent }>({ url: `/api/pairs/${id}/meeting`, method: 'POST', data }),
  recommendations: (memberId: string, limit = 6) =>
    request<{ target: Member; list: MatchRec[]; total: number }>({
      url: '/api/match/recommendations',
      data: { memberId, limit },
    }),
  // 看板状态流转：更新某条牵线记录状态（跟进/约见/完成/失败）
  // 注意：后端正确端点为 PUT /api/pairs/{id}（带 status 字段），
  // 子路径 /status 不存在（404）。状态机在服务端校验合法流转。
  updatePairStatus: (id: string, status: string, note?: string) =>
    request<Pair>({ url: `/api/pairs/${id}`, method: 'PUT', data: { status, note } }),
  matchScore: (maleId: string, femaleId: string) =>
    request<{ score: number; reasons: string[]; dimensions: Record<string, number>; male: Member; female: Member }>({
      url: '/api/match/score',
      data: { maleId, femaleId },
    }),

  // —— 日程 ——
  calendarEvents: (params?: { year?: number; month?: number; from?: string; to?: string; type?: string }) =>
    request<CalendarEvent[]>({ url: '/api/calendar/events', data: params }),
  calendarToday: () => request<CalendarEvent[]>({ url: '/api/calendar/today' }),
  createCalendarEvent: (data: Partial<CalendarEvent>) =>
    request<CalendarEvent>({ url: '/api/calendar/events', method: 'POST', data }),
  deleteCalendarEvent: (id: string) =>
    request<null>({ url: `/api/calendar/events/${id}`, method: 'DELETE' }),

  // —— 消息 ——
  conversations: (kind?: string) =>
    request<Conversation[]>({ url: '/api/conversations', data: kind ? { kind } : {} }),
  conversationUnread: () => request<Record<string, any>>({ url: '/api/conversations/unread' }),
  conversationDetail: (id: string) =>
    request<Conversation & { messages: MessageItem[]; member?: Member }>({ url: `/api/conversations/${id}` }),
  createConversation: (memberId: string) =>
    request<Conversation>({ url: '/api/conversations', method: 'POST', data: { memberId } }),
  sendMessage: (id: string, data: { content: string; confirmed?: boolean }) =>
    request<{ ok: boolean; blocked?: boolean; moderation?: any; message?: MessageItem }>({
      url: `/api/conversations/${id}/messages`,
      method: 'POST',
      data,
    }),
  replySuggest: (id: string) =>
    request<{ suggestions: string[]; source?: string }>({ url: `/api/conversations/${id}/reply-suggest`, method: 'POST' }),

  // —— AI 红娘助手 ——
  // 约会教练：基于牵线进展给出下一步约会建议 + 话题/穿搭话术库（AI 不可用时本地降级）
  aiCoachNextStep: (pairId: string) =>
    request<{
      stage?: string
      stageLabel?: string
      nextStep?: string
      actionWindow?: string
      topics?: string[]
      openers?: string[]
      deepenQuestions?: string[]
      tips?: string[]
      outfit?: string[]
      warnings?: string[]
      source?: string
      [k: string]: any
    }>({ url: `/api/ai/coach/next-step/${pairId}` }),
  // 会员画像教练（可选）：完整度诊断 + 补全清单 + 沟通脚本
  aiCoachProfile: (memberId: string) =>
    request<{
      score?: number
      level?: string
      verdict?: string
      missing?: { field: string; label: string; action: string; priority: string }[]
      quickWins?: any[]
      script?: string
      source?: string
      [k: string]: any
    }>({ url: `/api/ai/coach/profile/${memberId}` }),
}

export function errMsg(e: any): string {
  return (e && e.message) || '操作失败'
}
