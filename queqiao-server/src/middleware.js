// 中间件：请求日志、鉴权、404 与统一 JSON 错误处理
import { timingSafeEqual } from 'node:crypto'
import { authDisabled, config } from './config.js'
import { HttpError } from './util.js'

// ---------------- 请求日志 ----------------
export function requestLogger(req, res, next) {
  if (!config.requestLog) return next()
  const start = process.hrtime.bigint()
  res.on('finish', () => {
    const ms = Number(process.hrtime.bigint() - start) / 1e6
    console.log(`[req] ${req.method} ${req.originalUrl} ${res.statusCode} ${ms.toFixed(1)}ms`)
  })
  next()
}

// ---------------- 鉴权 ----------------
function safeEqual(a, b) {
  const bufA = Buffer.from(String(a))
  const bufB = Buffer.from(String(b))
  if (bufA.length !== bufB.length) return false
  return timingSafeEqual(bufA, bufB)
}

function extractToken(req) {
  const auth = req.get('authorization')
  if (auth && /^Bearer\s+/i.test(auth)) return auth.replace(/^Bearer\s+/i, '').trim()
  return req.get('x-api-token') || ''
}

/** 解析 token，标记 req.authorized；未配置 API_TOKEN 时为本地开发模式，全部视为已授权 */
export function authenticate(req, _res, next) {
  req.authorized = authDisabled ? true : safeEqual(extractToken(req), config.apiToken)
  next()
}

/** 写操作强制鉴权 */
export function requireAuth(req, _res, next) {
  if (req.authorized) return next()
  next(new HttpError(401, '缺少或无效的访问令牌'))
}

// ---------------- 404 / 错误 ----------------
export function notFoundHandler(req, res) {
  res.status(404).json({ error: '接口不存在', path: req.originalUrl })
}

// eslint-disable-next-line no-unused-vars -- Express 依靠四参数签名识别错误中间件
export function errorHandler(err, req, res, next) {
  // express.json() 解析失败会抛出带 status 的 SyntaxError，默认返回 HTML 错误页
  if (err?.type === 'entity.parse.failed' || err instanceof SyntaxError) {
    return res.status(400).json({ error: '请求体不是合法的 JSON' })
  }
  if (err?.type === 'entity.too.large') {
    return res.status(413).json({ error: '请求体过大' })
  }
  if (err instanceof HttpError) {
    const body = { error: err.message }
    if (err.details) body.details = err.details
    return res.status(err.status).json(body)
  }
  console.error(`[error] ${req.method} ${req.originalUrl}`, err)
  res.status(500).json({
    error: '服务内部错误',
    ...(config.isProd ? {} : { detail: err?.message }),
  })
}

/** 包裹 async handler，异常统一交给 errorHandler */
export const asyncHandler = (fn) => (req, res, next) => Promise.resolve(fn(req, res, next)).catch(next)
