// 请求体校验：字段白名单 + 类型/枚举/范围校验 + 部分更新
import { HttpError } from './util.js'

const isRecord = (v) => v !== null && typeof v === 'object' && !Array.isArray(v)

function checkField(name, rule, input, errors) {
  let v = typeof input === 'string' ? input.trim() : input

  switch (rule.type) {
    case 'string': {
      if (typeof v !== 'string') {
        errors.push(`${name} 必须是字符串`)
        return undefined
      }
      if (rule.min !== undefined && v.length < rule.min) errors.push(`${name} 长度不能小于 ${rule.min}`)
      if (rule.max !== undefined && v.length > rule.max) errors.push(`${name} 长度不能超过 ${rule.max}`)
      return v
    }
    case 'int': {
      if (typeof v === 'string' && v !== '' && Number.isFinite(Number(v))) v = Number(v)
      if (typeof v !== 'number' || !Number.isInteger(v)) {
        errors.push(`${name} 必须是整数`)
        return undefined
      }
      if (rule.min !== undefined && v < rule.min) errors.push(`${name} 不能小于 ${rule.min}`)
      if (rule.max !== undefined && v > rule.max) errors.push(`${name} 不能大于 ${rule.max}`)
      return v
    }
    case 'enum': {
      if (!rule.values.includes(v)) {
        errors.push(`${name} 只能是 ${rule.values.join(' / ')}`)
        return undefined
      }
      return v
    }
    case 'bool': {
      if (typeof v !== 'boolean') {
        errors.push(`${name} 必须是布尔值`)
        return undefined
      }
      return v
    }
    case 'stringArray': {
      if (!Array.isArray(v)) {
        errors.push(`${name} 必须是数组`)
        return undefined
      }
      if (rule.maxItems !== undefined && v.length > rule.maxItems) {
        errors.push(`${name} 最多 ${rule.maxItems} 项`)
        return undefined
      }
      if (!v.every((x) => typeof x === 'string' && x.length <= 40)) {
        errors.push(`${name} 的每一项必须是长度不超过 40 的字符串`)
        return undefined
      }
      return v.map((x) => x.trim())
    }
    case 'phone': {
      if (typeof v !== 'string' || !/^1[3-9]\d{9}$/.test(v.replace(/[\s-]/g, ''))) {
        errors.push(`${name} 必须是合法的 11 位手机号`)
        return undefined
      }
      return v.replace(/[\s-]/g, '')
    }
    case 'date': {
      if (typeof v !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(v) || Number.isNaN(Date.parse(v))) {
        errors.push(`${name} 必须是 YYYY-MM-DD 格式的日期`)
        return undefined
      }
      return v
    }
    case 'time': {
      if (typeof v !== 'string' || !/^([01]\d|2[0-3]):[0-5]\d$/.test(v)) {
        errors.push(`${name} 必须是 HH:mm 格式的时间`)
        return undefined
      }
      return v
    }
    case 'object': {
      if (!isRecord(v)) {
        errors.push(`${name} 必须是对象`)
        return undefined
      }
      const nested = validate(rule.schema, v)
      for (const e of nested.errors) errors.push(`${name}.${e}`)
      return nested.value
    }
    default:
      return undefined
  }
}

/**
 * 按 schema 白名单校验；未声明的字段一律忽略（不会写入业务对象）
 * @returns {{ value: object, errors: string[], ignored: string[] }}
 */
export function validate(schema, body) {
  const errors = []
  const ignored = []
  const value = {}
  const input = isRecord(body) ? body : {}

  for (const key of Object.keys(input)) {
    if (!Object.hasOwn(schema, key)) {
      ignored.push(key)
      continue
    }
    if (input[key] === undefined) continue
    if (input[key] === null && schema[key].nullable) {
      value[key] = null
      continue
    }
    const before = errors.length
    const v = checkField(key, schema[key], input[key], errors)
    if (errors.length === before) value[key] = v
  }
  return { value, errors, ignored }
}

/** 校验失败直接抛出 400 */
export function validateOrThrow(schema, body, { required = [] } = {}) {
  const result = validate(schema, body)
  for (const key of required) {
    if (result.value[key] === undefined) result.errors.push(`${key} 为必填项`)
  }
  if (result.errors.length) {
    throw new HttpError(400, '请求参数校验失败', result.errors)
  }
  return result
}

// ---------------- 业务 schema ----------------
export const requirementsSchema = {
  ageMin: { type: 'int', min: 18, max: 99 },
  ageMax: { type: 'int', min: 18, max: 99 },
  heightMin: { type: 'int', min: 100, max: 250 },
  heightMax: { type: 'int', min: 100, max: 250 },
  education: { type: 'string', max: 20 },
  income: { type: 'string', max: 30 },
  city: { type: 'string', max: 30 },
  extra: { type: 'string', max: 120 },
}

/** 会员可写字段白名单：id / matchScore / status / verified / certs 等由服务端生成 */
export const memberSchema = {
  name: { type: 'string', min: 1, max: 40 },
  gender: { type: 'enum', values: ['男', '女'] },
  age: { type: 'int', min: 18, max: 99 },
  phone: { type: 'phone' },
  job: { type: 'string', max: 40 },
  city: { type: 'string', max: 30 },
  education: { type: 'enum', values: ['高中', '中专', '大专', '本科', '硕士', '博士'] },
  school: { type: 'string', max: 40 },
  height: { type: 'int', min: 100, max: 250 },
  weight: { type: 'int', min: 20, max: 300 },
  income: { type: 'string', max: 30 },
  constellation: { type: 'string', max: 10 },
  bloodType: { type: 'string', max: 10 },
  hometown: { type: 'string', max: 40 },
  residence: { type: 'string', max: 60 },
  marital: { type: 'enum', values: ['未婚', '离异', '丧偶'] },
  tags: { type: 'stringArray', maxItems: 20 },
  hobbies: { type: 'stringArray', maxItems: 30 },
  intro: { type: 'string', max: 1000 },
  note: { type: 'string', max: 1000 },
  idleDays: { type: 'int', min: 0, max: 3650 },
  requirements: { type: 'object', schema: requirementsSchema },
}

export const pairSchema = {
  maleId: { type: 'int', min: 1 },
  femaleId: { type: 'int', min: 1 },
  statusLabel: { type: 'string', max: 60 },
}

export const meetingSchema = {
  date: { type: 'date' },
  time: { type: 'time' },
  maleId: { type: 'int', min: 1 },
  femaleId: { type: 'int', min: 1 },
  place: { type: 'string', max: 120 },
  note: { type: 'string', max: 500 },
  highlight: { type: 'bool' },
}

export const messageSchema = {
  text: { type: 'string', min: 1, max: 1000 },
}
