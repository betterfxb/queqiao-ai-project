import assert from 'node:assert/strict'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import { once } from 'node:events'
import { after, describe, it } from 'node:test'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))

// 独立的临时数据文件，避免污染真实 db.json
const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'queqiao-api-'))
const dbFile = path.join(tmpDir, 'db.json')
fs.copyFileSync(path.join(__dirname, '..', 'data', 'db.json'), dbFile)

const TOKEN = 'test-token-abcdef123456'
process.env.NODE_ENV = 'test'
process.env.DB_FILE = dbFile
process.env.API_TOKEN = TOKEN
process.env.AI_BASE = 'http://127.0.0.1:59997' // 不可达，用于验证超时/回退
process.env.AI_TIMEOUT_MS = '300'
process.env.SAVE_DEBOUNCE_MS = '10'
process.env.REQUEST_LOG = 'false'
process.env.CORS_ORIGINS = '*'

const { default: app, flush } = await import('../src/app.js')
const { localDate } = await import('../src/util.js')

const server = app.listen(0, '127.0.0.1')
await once(server, 'listening')
const base = `http://127.0.0.1:${server.address().port}`

async function api(method, url, { body, token, raw, contentType } = {}) {
  const headers = {}
  if (body !== undefined || raw !== undefined) headers['Content-Type'] = contentType ?? 'application/json'
  if (token) headers.Authorization = `Bearer ${token}`
  const res = await fetch(base + url, {
    method,
    headers,
    body: raw ?? (body === undefined ? undefined : JSON.stringify(body)),
  })
  const text = await res.text()
  let json = null
  try {
    json = JSON.parse(text)
  } catch {
    /* 非 JSON 响应保持 null */
  }
  return { status: res.status, json, text, contentType: res.headers.get('content-type') }
}

after(async () => {
  await flush()
  server.close()
  fs.rmSync(tmpDir, { recursive: true, force: true })
})

describe('会员列表与真实计数', () => {
  it('① GET /api/members 返回真实计数，而不是硬编码的 1286', async () => {
    const { status, json } = await api('GET', '/api/members')
    assert.equal(status, 200)
    assert.notEqual(json.total, 1286)
    assert.equal(json.total, json.list.length)
    assert.equal(json.total, 9)
  })

  it('筛选后 total 跟随过滤结果变化', async () => {
    const { json } = await api('GET', '/api/members?gender=男')
    assert.equal(json.total, json.list.length)
    assert.ok(json.list.every((m) => m.gender === '男'))
    assert.equal(json.totalAll, 9)
  })
})

describe('本地时区日期', () => {
  it('② 今日约见基于当前本地日期统计，无硬编码 2026-08-08', async () => {
    const today = localDate()
    assert.match(today, /^\d{4}-\d{2}-\d{2}$/)

    const before = await api('GET', '/api/dashboard')
    assert.equal(before.json.date, today)
    const beforeCount = before.json.board.find((b) => b.key === 'meeting').value

    const created = await api('POST', '/api/meetings', {
      token: TOKEN,
      body: { date: today, time: '15:30', maleId: 2, femaleId: 1, place: '测试咖啡馆' },
    })
    assert.equal(created.status, 201)

    const after2 = await api('GET', '/api/dashboard')
    assert.equal(after2.json.board.find((b) => b.key === 'meeting').value, beforeCount + 1)
  })

  it('健康检查返回本地日期', async () => {
    const { json } = await api('GET', '/api/health')
    assert.equal(json.date, localDate())
  })
})

describe('请求体不能覆盖服务端字段', () => {
  it('③ POST /api/members 无法覆盖 id / status / matchScore / verified', async () => {
    const { status, json } = await api('POST', '/api/members', {
      token: TOKEN,
      body: {
        name: '测试会员',
        gender: '男',
        age: 30,
        phone: '13812341234',
        id: 9999,
        status: '被伪造的状态',
        matchScore: 100,
        verified: true,
        idleDays: 0,
      },
    })
    assert.equal(status, 201)
    assert.notEqual(json.id, 9999)
    assert.equal(json.status, '刚刚活跃')
    assert.equal(json.verified, false)
    assert.notEqual(json.matchScore, 100)
    assert.equal(json.phone, '138****1234') // 手机号脱敏
    for (const f of ['id', 'status', 'matchScore', 'verified']) assert.ok(json.ignoredFields.includes(f))
  })

  it('POST /api/meetings 无法覆盖 id / status', async () => {
    const { status, json } = await api('POST', '/api/meetings', {
      token: TOKEN,
      body: { date: '2026-09-01', time: '10:00', maleId: 4, femaleId: 3, id: 8888, status: '已确认' },
    })
    assert.equal(status, 201)
    assert.notEqual(json.id, 8888)
    assert.equal(json.status, '待确认')
  })

  it('缺少必填 name / gender 时返回 400', async () => {
    const { status, json } = await api('POST', '/api/members', { token: TOKEN, body: { age: 28 } })
    assert.equal(status, 400)
    assert.ok(json.details.some((d) => d.includes('name')))
    assert.ok(json.details.some((d) => d.includes('gender')))
  })
})

describe('会员更新校验', () => {
  it('④ PUT /api/members/:id 拒绝非法字段值', async () => {
    const bad1 = await api('PUT', '/api/members/1', { token: TOKEN, body: { age: 'abc' } })
    assert.equal(bad1.status, 400)
    assert.ok(bad1.json.details.some((d) => d.includes('age')))

    const bad2 = await api('PUT', '/api/members/1', { token: TOKEN, body: { idleDays: -99 } })
    assert.equal(bad2.status, 400)

    const bad3 = await api('PUT', '/api/members/1', { token: TOKEN, body: { gender: '未知' } })
    assert.equal(bad3.status, 400)
  })

  it('部分更新只覆盖传入字段', async () => {
    const { status, json } = await api('PUT', '/api/members/3', { token: TOKEN, body: { city: '南京' } })
    assert.equal(status, 200)
    assert.equal(json.city, '南京')
    assert.equal(json.name, '王雅婷')
    assert.equal(json.age, 26)
  })

  it('idleDays 更新后 status 由服务端派生', async () => {
    const { json } = await api('PUT', '/api/members/3', { token: TOKEN, body: { idleDays: 4 } })
    assert.equal(json.status, '4天未跟进')
  })

  it('不存在的会员返回 404 JSON', async () => {
    const { status, json } = await api('PUT', '/api/members/99999', { token: TOKEN, body: { city: '上海' } })
    assert.equal(status, 404)
    assert.equal(json.error, '会员不存在')
  })
})

describe('智能匹配', () => {
  it('⑤ 缺少 memberId 返回 400', async () => {
    const { status, json } = await api('GET', '/api/match/recommendations')
    assert.equal(status, 400)
    assert.equal(json.error, 'memberId 为必填项')
  })

  it('memberId 非法返回 400，不存在返回 404', async () => {
    assert.equal((await api('GET', '/api/match/recommendations?memberId=abc')).status, 400)
    assert.equal((await api('GET', '/api/match/recommendations?memberId=99999')).status, 404)
  })

  it('AI 不可用时超时回退本地规则，且评分确定性', async () => {
    const first = await api('GET', '/api/match/recommendations?memberId=2')
    assert.equal(first.status, 200)
    assert.equal(first.json.source, 'rule')
    const second = await api('GET', '/api/match/recommendations?memberId=2')
    assert.deepEqual(first.json.list, second.json.list)
  })
})

describe('统一错误处理', () => {
  it('⑥ 非法 JSON 返回 JSON 格式的 400，而不是 HTML', async () => {
    const { status, json, contentType } = await api('POST', '/api/members', {
      token: TOKEN,
      raw: '{"name": ',
    })
    assert.equal(status, 400)
    assert.match(contentType, /application\/json/)
    assert.equal(json.error, '请求体不是合法的 JSON')
  })

  it('未知路由返回 JSON 404', async () => {
    const { status, json, contentType } = await api('GET', '/api/not-exist')
    assert.equal(status, 404)
    assert.match(contentType, /application\/json/)
    assert.equal(json.error, '接口不存在')
  })
})

describe('鉴权与 PII 脱敏', () => {
  it('无 token 的写操作返回 401', async () => {
    const { status, json } = await api('POST', '/api/members', { body: { name: 'x', gender: '女' } })
    assert.equal(status, 401)
    assert.equal(json.error, '缺少或无效的访问令牌')
  })

  it('错误 token 同样被拒绝', async () => {
    assert.equal((await api('PUT', '/api/members/1', { token: 'wrong', body: { city: '上海' } })).status, 401)
  })

  it('未授权读取会员详情时隐藏收入/住址等 PII', async () => {
    const anon = await api('GET', '/api/members/1')
    assert.equal(anon.status, 200)
    assert.equal(anon.json.income, '保密')
    assert.equal(anon.json.residence, undefined)
    assert.equal(anon.json.hometown, undefined)
    assert.equal(anon.json.note, undefined)

    const authed = await api('GET', '/api/members/1', { token: TOKEN })
    assert.equal(authed.json.income, '50-80万')
    assert.equal(authed.json.residence, '上海浦东')
  })
})

describe('牵线校验', () => {
  it('同性别配对被拒绝', async () => {
    const { status } = await api('POST', '/api/pairs', { token: TOKEN, body: { maleId: 2, femaleId: 4 } })
    assert.equal(status, 400)
  })

  it('创建成功后重复配对返回 409，评分为确定性整数', async () => {
    const first = await api('POST', '/api/pairs', { token: TOKEN, body: { maleId: 2, femaleId: 3 } })
    assert.equal(first.status, 201)
    assert.ok(Number.isInteger(first.json.matchScore))
    assert.equal(first.json.status, '牵线中')

    const dup = await api('POST', '/api/pairs', { token: TOKEN, body: { maleId: 2, femaleId: 3 } })
    assert.equal(dup.status, 409)
  })
})

describe('会话闭环', () => {
  it('PATCH 已读 + POST 发送消息', async () => {
    const read = await api('PATCH', '/api/conversations/1/read', { token: TOKEN })
    assert.equal(read.status, 200)
    assert.equal(read.json.unread, 0)

    const sent = await api('POST', '/api/conversations/1/messages', {
      token: TOKEN,
      body: { text: '周六下午三点，静安嘉里中心可以吗？' },
    })
    assert.equal(sent.status, 201)
    assert.equal(sent.json.conversationId, 1)

    const list = await api('GET', '/api/conversations/1/messages')
    assert.equal(list.json.list.length, 1)

    const empty = await api('POST', '/api/conversations/1/messages', { token: TOKEN, body: { text: '' } })
    assert.equal(empty.status, 400)

    const missing = await api('PATCH', '/api/conversations/9999/read', { token: TOKEN })
    assert.equal(missing.status, 404)
  })
})

describe('持久化', () => {
  it('落盘为合法 JSON 且包含 __seq 游标', async () => {
    await flush()
    const saved = JSON.parse(fs.readFileSync(dbFile, 'utf-8'))
    assert.ok(saved.__seq)
    assert.ok(saved.__seq.members >= 10)
    assert.ok(saved.members.length >= 10)
  })
})
