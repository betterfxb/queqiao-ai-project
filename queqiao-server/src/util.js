// 通用工具：本地时区日期、PII 脱敏、确定性评分、HTTP 错误
import { config } from './config.js'

export class HttpError extends Error {
  constructor(status, message, details) {
    super(message)
    this.name = 'HttpError'
    this.status = status
    if (details) this.details = details
  }
}

// ---------------- 日期（本地时区，修复 toISOString 取 UTC 的 bug） ----------------
const dateFmt = new Intl.DateTimeFormat('en-CA', {
  timeZone: config.timezone,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
})
const timeFmt = new Intl.DateTimeFormat('en-GB', {
  timeZone: config.timezone,
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  hour12: false,
})

/** 本地时区日期 YYYY-MM-DD */
export const localDate = (d = new Date()) => dateFmt.format(d)
/** 本地时区时间 HH:mm:ss */
export const localTime = (d = new Date()) => timeFmt.format(d)
/** 本地时区日期时间 YYYY-MM-DD HH:mm:ss */
export const localDateTime = (d = new Date()) => `${localDate(d)} ${localTime(d)}`

// ---------------- 脱敏 ----------------
export function maskPhone(phone) {
  if (phone === undefined || phone === null || phone === '') return phone
  const s = String(phone).replace(/[\s-]/g, '')
  if (s.length < 7) return '***'
  return `${s.slice(0, 3)}****${s.slice(-4)}`
}

// 未授权时隐藏的敏感字段
const SENSITIVE_HIDDEN = ['residence', 'hometown', 'note', 'noteUpdatedAt', 'rawText', 'idCard']

/**
 * 会员对外输出：手机号始终掩码；未授权时进一步隐藏收入/住址/红娘备注
 */
export function publicMember(m, authorized = false) {
  if (!m) return m
  const out = { ...m }
  if ('phone' in out) out.phone = maskPhone(out.phone)
  if (!authorized) {
    for (const f of SENSITIVE_HIDDEN) delete out[f]
    if ('income' in out) out.income = '保密'
  }
  return out
}

/** 列表/关联展示用的最小字段集 */
export const memberBrief = (m) =>
  m && {
    id: m.id,
    name: m.name,
    gender: m.gender,
    age: m.age,
    job: m.job,
    avatarBg: m.avatarBg,
    avatarColor: m.avatarColor,
  }

/** 发送给 AI 服务的脱敏画像：不含姓名/手机号/收入/住址/备注 */
export const aiProfile = (m) =>
  m && {
    id: m.id,
    gender: m.gender,
    age: m.age,
    job: m.job,
    city: m.city,
    education: m.education,
    height: m.height,
    tags: m.tags ?? [],
    hobbies: m.hobbies ?? [],
    requirements: m.requirements ?? {},
    matchScore: m.matchScore,
  }

// ---------------- 确定性评分（替代 Math.random） ----------------
const clamp = (n, min, max) => Math.min(max, Math.max(min, n))
const EDU_RANK = { 高中: 1, 中专: 1, 大专: 2, 本科: 3, 硕士: 4, 博士: 5 }
const eduRank = (v) => EDU_RANK[String(v ?? '').trim()] ?? 0
const filled = (v) =>
  v !== undefined && v !== null && v !== '' && !(Array.isArray(v) && v.length === 0)

/** 会员画像完整度评分（60~100，确定性） */
export function scoreMemberProfile(m) {
  let s = 60
  for (const f of ['name', 'age', 'job', 'city', 'education', 'height', 'income', 'intro']) {
    if (filled(m[f])) s += 3
  }
  if (filled(m.tags)) s += 3
  if (filled(m.hobbies)) s += 3
  if (m.requirements && Object.keys(m.requirements).length > 0) s += 3
  if (m.verified) s += 5
  return clamp(s, 0, 100)
}

/** 配对契合度评分（确定性规则，0~100） */
export function scorePair(male, female) {
  let s = 55
  const ageGap = Math.abs(Number(male.age ?? 0) - Number(female.age ?? 0))
  if (male.age && female.age) {
    if (ageGap <= 3) s += 12
    else if (ageGap <= 6) s += 8
    else if (ageGap <= 10) s += 4
  }
  if (male.city && female.city && male.city === female.city) s += 10
  const em = eduRank(male.education)
  const ef = eduRank(female.education)
  if (em && ef) s += em === ef ? 6 : Math.abs(em - ef) === 1 ? 4 : 1
  if (male.height && female.height) {
    const d = Number(male.height) - Number(female.height)
    if (d >= 8 && d <= 25) s += 6
    else if (d > 0) s += 3
  }
  if (matchRequirement(female.requirements, male)) s += 5
  if (matchRequirement(male.requirements, female)) s += 5
  const overlap = intersectCount(
    [...(male.hobbies ?? []), ...(male.tags ?? [])],
    [...(female.hobbies ?? []), ...(female.tags ?? [])],
  )
  s += Math.min(overlap * 2, 6)
  if (male.verified && female.verified) s += 4
  return clamp(Math.round(s), 0, 100)
}

function matchRequirement(req, other) {
  if (!req || typeof req !== 'object') return false
  if (req.ageMin && Number(other.age) < Number(req.ageMin)) return false
  if (req.ageMax && Number(other.age) > Number(req.ageMax)) return false
  if (req.heightMin && Number(other.height) < Number(req.heightMin)) return false
  if (req.city && other.city && req.city !== other.city) return false
  return true
}

function intersectCount(a, b) {
  const set = new Set(b)
  let n = 0
  for (const x of new Set(a)) if (set.has(x)) n += 1
  return n
}

/** 根据 idleDays 派生活跃状态，避免客户端伪造 status */
export const deriveStatus = (idleDays) =>
  Number(idleDays) > 0 ? `${Number(idleDays)}天未跟进` : '刚刚活跃'

/** 带超时的 AI 服务调用 */
export async function callAi(pathname, payload) {
  const res = await fetch(`${config.aiBase}${pathname}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
    signal: AbortSignal.timeout(config.aiTimeoutMs),
  })
  if (!res.ok) throw new Error(`AI 服务返回 ${res.status}`)
  return res.json()
}
