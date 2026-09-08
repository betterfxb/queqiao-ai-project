// 数据访问层：JSON 文件持久化
// - 读：容错解析 + 顶层结构校验 + 损坏回退种子
// - 写：异步 + 原子（临时文件 rename）+ 节流合并
// - id：db.__seq 全局游标持久化，避免删除后复用
// - 查：Map<id, item> 索引，避免 O(n) find
import fs from 'node:fs'
import fsp from 'node:fs/promises'
import path from 'node:path'
import { config } from './config.js'
import { seed } from './seed.js'
import { deriveStatus, scoreMemberProfile } from './util.js'

export const COLLECTIONS = ['members', 'pairs', 'meetings', 'conversations', 'messages']

const DEFAULT_AVATAR_BG = 'linear-gradient(135deg, #F4C6D0 0%, #E8A0B0 100%)'
const DEFAULT_AVATAR_COLOR = '#8B254A'

let db = null
const indexes = new Map()

const isRecord = (v) => v !== null && typeof v === 'object' && !Array.isArray(v)
const maxId = (list) => list.reduce((acc, x) => (Number.isInteger(x?.id) && x.id > acc ? x.id : acc), 0)

// ---------------- 读取 ----------------
function readDbFile() {
  if (!fs.existsSync(config.dbFile)) return { data: structuredClone(seed), fresh: true }
  try {
    const parsed = JSON.parse(fs.readFileSync(config.dbFile, 'utf-8'))
    if (!isRecord(parsed)) throw new Error('顶层结构必须是对象')
    if (!Array.isArray(parsed.members)) throw new Error('缺少 members 数组')
    return { data: parsed, fresh: false }
  } catch (e) {
    console.error(`[store] 读取 ${config.dbFile} 失败，回退种子数据：${e.message}`)
    backupCorruptFile()
    return { data: structuredClone(seed), fresh: true }
  }
}

function backupCorruptFile() {
  try {
    const backup = `${config.dbFile}.corrupt-${Date.now()}`
    fs.copyFileSync(config.dbFile, backup)
    console.error(`[store] 原文件已备份至 ${backup}`)
  } catch (e) {
    console.error(`[store] 备份损坏文件失败：${e.message}`)
  }
}

// ---------------- 结构规整（修复 schema 漂移） ----------------
function normalizeMember(raw) {
  const m = isRecord(raw) ? raw : {}
  const idleDays = Number.isFinite(Number(m.idleDays)) ? Math.max(0, Number(m.idleDays)) : 0
  const base = {
    id: Number(m.id),
    name: m.name ?? '未命名会员',
    gender: m.gender === '男' ? '男' : '女',
    age: m.age,
    job: m.job ?? '',
    city: m.city ?? '',
    education: m.education ?? '',
    verified: m.verified === true,
    tags: Array.isArray(m.tags) ? m.tags : [],
    hobbies: Array.isArray(m.hobbies) ? m.hobbies : [],
    certs: Array.isArray(m.certs) ? m.certs : [],
    requirements: isRecord(m.requirements) ? m.requirements : {},
    idleDays,
    status: typeof m.status === 'string' && m.status ? m.status : deriveStatus(idleDays),
    avatarBg: m.avatarBg ?? DEFAULT_AVATAR_BG,
    avatarColor: m.avatarColor ?? DEFAULT_AVATAR_COLOR,
  }
  const merged = { ...m, ...base }
  merged.matchScore = Number.isFinite(Number(m.matchScore))
    ? Number(m.matchScore)
    : scoreMemberProfile(merged)
  return merged
}

function normalize(data) {
  if (!isRecord(data.matchmaker)) data.matchmaker = structuredClone(seed.matchmaker)
  for (const name of COLLECTIONS) {
    if (!Array.isArray(data[name])) data[name] = structuredClone(seed[name] ?? [])
    data[name] = data[name].filter((x) => isRecord(x) && Number.isInteger(Number(x.id)))
  }
  data.members = data.members.map(normalizeMember)

  const seq = isRecord(data.__seq) ? data.__seq : {}
  data.__seq = {}
  for (const name of COLLECTIONS) {
    const stored = Number(seq[name])
    data.__seq[name] = Math.max(Number.isFinite(stored) ? stored : 0, maxId(data[name]))
  }
  return data
}

function buildIndexes() {
  indexes.clear()
  for (const name of COLLECTIONS) {
    const map = new Map()
    for (const item of db[name]) map.set(Number(item.id), item)
    indexes.set(name, map)
  }
}

// ---------------- 公共 API ----------------
export function load() {
  if (db) return db
  const { data, fresh } = readDbFile()
  const before = fresh ? null : JSON.stringify(data)
  db = normalize(data)
  buildIndexes()
  if (fresh || before !== JSON.stringify(db)) save()
  return db
}

export function getDb() {
  return load()
}

/** O(1) 主键查询 */
export function findById(collection, id) {
  load()
  const n = Number(id)
  if (!Number.isInteger(n)) return undefined
  return indexes.get(collection)?.get(n)
}

/** 插入并同步索引 */
export function insert(collection, item) {
  load()
  db[collection].push(item)
  indexes.get(collection).set(Number(item.id), item)
  return item
}

/** 全局游标自增，删除记录也不会复用 id */
export function nextId(collection) {
  load()
  db.__seq[collection] = (db.__seq[collection] ?? 0) + 1
  return db.__seq[collection]
}

// ---------------- 写入：异步 + 原子 + 节流 ----------------
let dirty = false
let timer = null
let chain = Promise.resolve()

export function save() {
  dirty = true
  if (timer) return
  timer = setTimeout(() => {
    timer = null
    void flush()
  }, config.saveDebounceMs)
  if (typeof timer.unref === 'function') timer.unref()
}

export function flush() {
  if (timer) {
    clearTimeout(timer)
    timer = null
  }
  chain = chain.then(async () => {
    if (!dirty) return
    dirty = false
    try {
      await writeAtomic()
    } catch (e) {
      dirty = true
      console.error(`[store] 落盘失败：${e.message}`)
    }
  })
  return chain
}

async function writeAtomic() {
  const dir = path.dirname(config.dbFile)
  await fsp.mkdir(dir, { recursive: true })
  const tmp = `${config.dbFile}.${process.pid}.tmp`
  await fsp.writeFile(tmp, JSON.stringify(db, null, 2), 'utf-8')
  await fsp.rename(tmp, config.dbFile)
}
