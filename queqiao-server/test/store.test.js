import assert from 'node:assert/strict'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import { after, describe, it } from 'node:test'

// 故意写入损坏的 JSON，验证不会导致进程崩溃
const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'queqiao-store-'))
const dbFile = path.join(tmpDir, 'db.json')
fs.writeFileSync(dbFile, '{ "members": [ {"id": 1, ', 'utf-8')

process.env.NODE_ENV = 'test'
process.env.DB_FILE = dbFile
process.env.SAVE_DEBOUNCE_MS = '10'

const store = await import('../src/store.js')

after(() => fs.rmSync(tmpDir, { recursive: true, force: true }))

describe('store 容错与持久化', () => {
  it('db.json 损坏时回退种子数据而不是崩溃', () => {
    const db = store.load()
    assert.equal(db.members.length, 8)
    assert.equal(db.members[0].name, '李梦琪')
    const backups = fs.readdirSync(tmpDir).filter((f) => f.includes('.corrupt-'))
    assert.equal(backups.length, 1)
  })

  it('顶层结构被校验并补齐缺失集合', () => {
    const db = store.load()
    for (const c of store.COLLECTIONS) assert.ok(Array.isArray(db[c]), `${c} 应为数组`)
    assert.ok(db.matchmaker && typeof db.matchmaker === 'object')
  })

  it('__seq 游标自增，删除最大 id 后不复用', () => {
    const db = store.load()
    const first = store.nextId('members')
    assert.equal(first, 9)
    store.insert('members', { id: first, name: '临时', gender: '女' })
    db.members = db.members.filter((m) => m.id !== first)
    assert.equal(store.nextId('members'), 10)
  })

  it('findById 走索引且返回同一引用', () => {
    const db = store.load()
    assert.equal(store.findById('members', 1), db.members[0])
    assert.equal(store.findById('members', '1'), db.members[0])
    assert.equal(store.findById('members', 'abc'), undefined)
  })

  it('原子写：flush 后文件为完整合法 JSON，且无临时文件残留', async () => {
    store.save()
    await store.flush()
    const raw = fs.readFileSync(dbFile, 'utf-8')
    const parsed = JSON.parse(raw)
    assert.ok(Array.isArray(parsed.members))
    assert.equal(fs.readdirSync(tmpDir).filter((f) => f.endsWith('.tmp')).length, 0)
  })
})
