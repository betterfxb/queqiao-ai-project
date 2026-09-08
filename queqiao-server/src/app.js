// 鹊桥 · 红娘工作台 — Express 应用（只负责构建 app，不负责监听）
import express from 'express'
import cors from 'cors'
import { config } from './config.js'
import { findById, flush, getDb, insert, nextId, save } from './store.js'
import {
  HttpError,
  aiProfile,
  callAi,
  deriveStatus,
  localDate,
  localDateTime,
  memberBrief,
  publicMember,
  scoreMemberProfile,
  scorePair,
} from './util.js'
import {
  asyncHandler,
  authenticate,
  errorHandler,
  notFoundHandler,
  requestLogger,
  requireAuth,
} from './middleware.js'
import { meetingSchema, memberSchema, messageSchema, pairSchema, validateOrThrow } from './validate.js'

const app = express()
const db = getDb()

// ---------------- 中间件 ----------------
app.use(
  cors({
    origin(origin, cb) {
      if (!origin || config.corsOrigins.includes('*') || config.corsOrigins.includes(origin)) {
        return cb(null, true)
      }
      cb(null, false)
    },
    credentials: true,
  }),
)
app.use(express.json({ limit: config.jsonLimit }))
app.use(requestLogger)
app.use(authenticate)

// ---------------- 公共小工具 ----------------
const member = (id) => findById('members', id)

function mustFindMember(id, label = '会员') {
  const m = member(id)
  if (!m) throw new HttpError(404, `${label}不存在`)
  return m
}

const withPeople = (row) => ({
  ...row,
  male: memberBrief(member(row.maleId)),
  female: memberBrief(member(row.femaleId)),
})

// ---------------- 工作台 ----------------
app.get('/api/dashboard', (req, res) => {
  const today = localDate()
  const followups = db.members
    .filter((m) => m.idleDays > 0)
    .sort((a, b) => b.idleDays - a.idleDays)
    .slice(0, 5)
    .map((m) => publicMember(m, req.authorized))

  res.json({
    date: today,
    matchmaker: db.matchmaker,
    board: [
      { key: 'matching', label: '牵线中', value: db.pairs.filter((p) => p.status === '牵线中').length, unit: '对', icon: 'stat-match' },
      { key: 'pending', label: '待跟进', value: db.members.filter((m) => m.idleDays > 0).length, unit: '人', icon: 'stat-clock' },
      { key: 'meeting', label: '今日约见', value: db.meetings.filter((x) => x.date === today).length, unit: '场', icon: 'stat-calendar' },
    ],
    followups,
  })
})

// ---------------- 会员 ----------------
app.get('/api/members', (req, res) => {
  const { keyword = '', gender = '' } = req.query
  let list = db.members
  if (keyword) {
    const k = String(keyword)
    list = list.filter((m) => [m.name, m.job, m.city, String(m.id)].some((v) => v?.includes(k)))
  }
  if (gender) list = list.filter((m) => m.gender === String(gender))

  const total = list.length
  const page = Number.parseInt(req.query.page, 10)
  const pageSize = Number.parseInt(req.query.pageSize, 10)
  let paged = list
  if (Number.isInteger(page) && page > 0 && Number.isInteger(pageSize) && pageSize > 0) {
    paged = list.slice((page - 1) * pageSize, page * pageSize)
  }

  res.json({
    total,
    totalAll: db.members.length,
    list: paged.map((m) => publicMember(m, req.authorized)),
  })
})

app.get('/api/members/:id', (req, res) => {
  res.json(publicMember(mustFindMember(req.params.id), req.authorized))
})

app.post(
  '/api/members',
  requireAuth,
  asyncHandler(async (req, res) => {
    const body = { ...(req.body ?? {}) }
    const rawText = typeof body.rawText === 'string' ? body.rawText : ''
    delete body.rawText

    // rawText 智能建档：AI 抽取的字段同样要过白名单校验
    let aiFields = {}
    if (rawText) {
      try {
        const ai = await callAi('/parse', { text: rawText })
        if (ai && typeof ai.fields === 'object' && ai.fields) aiFields = ai.fields
      } catch (e) {
        console.warn(`[ai] parse 不可用：${e.message}`)
      }
    }

    const { value, ignored } = validateOrThrow(memberSchema, { ...aiFields, ...body }, {
      required: ['name', 'gender'],
    })

    const idleDays = value.idleDays ?? 0
    // 先展开业务字段，再强制覆盖服务端生成字段
    const created = {
      ...value,
      id: nextId('members'),
      idleDays,
      status: deriveStatus(idleDays),
      verified: false,
      certs: [],
      tags: value.tags ?? [],
      hobbies: value.hobbies ?? [],
      requirements: value.requirements ?? {},
      avatarBg: 'linear-gradient(135deg, #F4C6D0 0%, #E8A0B0 100%)',
      avatarColor: '#8B254A',
      createdAt: localDate(),
    }
    created.matchScore = scoreMemberProfile(created)

    insert('members', created)
    save()
    res.status(201).json({ ...publicMember(created, req.authorized), ignoredFields: ignored })
  }),
)

app.put('/api/members/:id', requireAuth, (req, res) => {
  const m = mustFindMember(req.params.id)
  const { value, ignored } = validateOrThrow(memberSchema, req.body)
  if (Object.keys(value).length === 0) throw new HttpError(400, '没有可更新的字段', ignored)

  Object.assign(m, value) // 部分更新，仅覆盖传入且校验通过的字段
  if (value.idleDays !== undefined) m.status = deriveStatus(m.idleDays)
  if (value.note !== undefined) m.noteUpdatedAt = localDate()
  m.matchScore = scoreMemberProfile(m)
  m.updatedAt = localDateTime()

  save()
  res.json({ ...publicMember(m, req.authorized), ignoredFields: ignored })
})

// ---------------- 牵线 ----------------
app.get('/api/pairs', (req, res) => {
  const { status = '' } = req.query
  let list = db.pairs.map(withPeople)
  if (status) list = list.filter((p) => p.status === String(status))
  res.json({ total: list.length, list })
})

app.post('/api/pairs', requireAuth, (req, res) => {
  const { value } = validateOrThrow(pairSchema, req.body, { required: ['maleId', 'femaleId'] })
  const male = mustFindMember(value.maleId, '男方会员')
  const female = mustFindMember(value.femaleId, '女方会员')

  if (male.id === female.id) throw new HttpError(400, '不能与自己配对')
  if (male.gender !== '男' || female.gender !== '女') {
    throw new HttpError(400, 'maleId 必须为男性会员、femaleId 必须为女性会员')
  }
  const duplicated = db.pairs.some((p) => p.maleId === male.id && p.femaleId === female.id)
  if (duplicated) throw new HttpError(409, '该配对已存在')

  const pair = {
    ...value,
    id: nextId('pairs'),
    maleId: male.id,
    femaleId: female.id,
    matchScore: scorePair(male, female),
    status: '牵线中',
    statusLabel: value.statusLabel ?? '等待双方确认',
    createdAt: localDate(),
  }
  insert('pairs', pair)
  save()
  res.status(201).json(withPeople(pair))
})

// 智能匹配推荐（代理 Python AI，失败时回退本地规则评分）
app.get(
  '/api/match/recommendations',
  asyncHandler(async (req, res) => {
    const raw = req.query.memberId
    if (raw === undefined || raw === '') throw new HttpError(400, 'memberId 为必填项')
    const id = Number(raw)
    if (!Number.isInteger(id) || id <= 0) throw new HttpError(400, 'memberId 必须是正整数')

    const target = mustFindMember(id)
    const candidates = db.members.filter((m) => m.id !== target.id && m.gender !== target.gender)

    try {
      // 只把脱敏画像发给 AI
      const ai = await callAi('/match', {
        target: aiProfile(target),
        candidates: candidates.map(aiProfile),
      })
      return res.json({ target: memberBrief(target), source: 'ai', ...ai })
    } catch (e) {
      console.warn(`[ai] match 不可用，回退本地规则：${e.message}`)
      const list = candidates
        .map((c) => ({
          member: memberBrief(c),
          matchScore: target.gender === '男' ? scorePair(target, c) : scorePair(c, target),
        }))
        .sort((a, b) => b.matchScore - a.matchScore)
        .slice(0, 5)
      return res.json({ target: memberBrief(target), source: 'rule', list })
    }
  }),
)

// ---------------- 约见 ----------------
app.get('/api/meetings', (req, res) => {
  const { date = '' } = req.query
  let list = db.meetings
  if (date) list = list.filter((m) => m.date === String(date))
  res.json({ total: list.length, today: localDate(), list: list.map(withPeople) })
})

app.post('/api/meetings', requireAuth, (req, res) => {
  const { value, ignored } = validateOrThrow(meetingSchema, req.body, {
    required: ['date', 'time', 'maleId', 'femaleId'],
  })
  const male = mustFindMember(value.maleId, '男方会员')
  const female = mustFindMember(value.femaleId, '女方会员')
  if (male.gender !== '男' || female.gender !== '女') {
    throw new HttpError(400, 'maleId 必须为男性会员、femaleId 必须为女性会员')
  }

  // 先展开业务字段，再强制覆盖服务端生成字段
  const meeting = {
    ...value,
    id: nextId('meetings'),
    maleId: male.id,
    femaleId: female.id,
    status: '待确认',
    createdAt: localDateTime(),
  }
  insert('meetings', meeting)
  save()
  res.status(201).json({ ...withPeople(meeting), ignoredFields: ignored })
})

// ---------------- 消息 ----------------
app.get('/api/conversations', (req, res) => {
  const { kind = '' } = req.query
  let list = db.conversations
  if (kind === 'member') list = list.filter((c) => c.kind === 'member')
  if (kind === 'system') list = list.filter((c) => c.kind === 'system' || c.kind === 'ai')
  res.json({ total: list.length, unread: db.conversations.reduce((n, c) => n + (c.unread || 0), 0), list })
})

app.get('/api/conversations/:id/messages', (req, res) => {
  const conv = findById('conversations', req.params.id)
  if (!conv) throw new HttpError(404, '会话不存在')
  res.json({ list: db.messages.filter((m) => m.conversationId === conv.id) })
})

app.patch('/api/conversations/:id/read', requireAuth, (req, res) => {
  const conv = findById('conversations', req.params.id)
  if (!conv) throw new HttpError(404, '会话不存在')
  conv.unread = 0
  save()
  res.json(conv)
})

app.post('/api/conversations/:id/messages', requireAuth, (req, res) => {
  const conv = findById('conversations', req.params.id)
  if (!conv) throw new HttpError(404, '会话不存在')
  const { value } = validateOrThrow(messageSchema, req.body, { required: ['text'] })

  const message = {
    id: nextId('messages'),
    conversationId: conv.id,
    from: 'matchmaker',
    text: value.text,
    createdAt: localDateTime(),
  }
  insert('messages', message)
  conv.snippet = value.text.slice(0, 60)
  conv.time = localDateTime().slice(11, 16)
  conv.unread = 0
  save()
  res.status(201).json(message)
})

// ---------------- AI 直通 ----------------
app.post(
  '/api/ai/parse',
  requireAuth,
  asyncHandler(async (req, res) => {
    const text = typeof req.body?.text === 'string' ? req.body.text : ''
    if (!text) throw new HttpError(400, 'text 为必填项')
    try {
      res.json(await callAi('/parse', { text }))
    } catch (e) {
      console.warn(`[ai] parse 不可用：${e.message}`)
      throw new HttpError(502, 'AI 服务不可用')
    }
  }),
)

// ---------------- 健康检查 ----------------
app.get('/api/health', (req, res) =>
  res.json({ status: 'ok', service: 'queqiao-server', env: config.nodeEnv, date: localDate() }),
)

// ---------------- 兜底 ----------------
app.use(notFoundHandler)
app.use(errorHandler)

export { app, flush }
export default app
