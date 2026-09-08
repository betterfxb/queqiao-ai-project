/**
 * HTTP 客户端（预留后端 :8080 / Python AI :8090 接口）
 * 安全增强：
 *  - 每个请求附带 HMAC 签名头（X-App/X-Ts/X-Nonce/X-Sign），服务端校验防篡改/防重放
 *  - 附带认证令牌 Authorization: Bearer <token>
 *  - 401 → 清除会话并跳登录；403 → 权限不足（保持登录态，仅提示）；429 → 提示限流（服务端风控）
 *  - 接口不可用时自动降级到传入的 mock 数据，保证前端原型可独立运行
 */

const API_BASE = (import.meta as any).env?.VITE_API_BASE ?? '/api'
const AI_BASE = (import.meta as any).env?.VITE_AI_BASE ?? '/ai'

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE'
  body?: unknown
  query?: Record<string, string | number | undefined>
  /** 接口失败时的降级数据 */
  mock?: unknown
  /** 跳过签名（极少数不需要签名的匿名端点） */
  skipSign?: boolean
}

function buildQuery(query?: RequestOptions['query']) {
  if (!query) return ''
  const usp = new URLSearchParams()
  Object.entries(query).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== '') usp.append(k, String(v))
  })
  const s = usp.toString()
  return s ? `?${s}` : ''
}

function getToken(): string {
  try {
    return sessionStorage.getItem('qq_token') ?? ''
  } catch {
    return ''
  }
}

function redirectToLogin() {
  if (location.hash.startsWith('#/login')) return
  // 记录当前路径，登录后回跳原页面
  const path = location.hash.replace(/^#/, '') || '/'
  location.hash = '#/login?redirect=' + encodeURIComponent(path)
}

/** 提取服务端返回的业务错误文案（GlobalExceptionHandler 统一为 { error: msg }） */
async function serverMessage(res: Response, fallback: string): Promise<string> {
  try {
    const text = await res.clone().text()
    if (!text) return fallback
    const json = JSON.parse(text)
    return json?.error || json?.message || fallback
  } catch {
    return fallback
  }
}

/** 权限不足错误：调用方可据此区分「无权限」与「网络/服务异常」 */
export class ForbiddenError extends Error {
  readonly status = 403
  constructor(message: string) {
    super(message)
    this.name = 'ForbiddenError'
  }
}

/** 请求超时（毫秒）：后端挂起时不再无限等待 */
const TIMEOUT_MS = 15_000
/** 网络/超时失败的一次性重试（4xx/5xx 不重试，交由上层处理） */
const MAX_RETRY = 1

async function raw<T>(base: string, path: string, opts: RequestOptions): Promise<T> {
  const url = `${base}${path}${buildQuery(opts.query)}`
  const method = opts.method ?? 'GET'
  const headers: Record<string, string> = { 'Content-Type': 'application/json' }

  const token = getToken()
  if (token) headers['Authorization'] = `Bearer ${token}`

  if (!opts.skipSign) {
    const { buildSignHeaders, pathnameOf } = await import('../security/sign')
    const signed = await buildSignHeaders(method, pathnameOf(`${base}${path}`))
    Object.assign(headers, signed)
  }

  const init: RequestInit = {
    method,
    headers,
    body: opts.body !== undefined ? JSON.stringify(opts.body) : undefined,
  }

  // fetch 阶段（网络/超时）可重试；HTTP 状态错误不重试
  let res: Response | null = null
  for (let attempt = 0; attempt <= MAX_RETRY; attempt++) {
    const ctrl = new AbortController()
    const timer = window.setTimeout(() => ctrl.abort(), TIMEOUT_MS)
    try {
      res = await fetch(url, { ...init, signal: ctrl.signal })
      window.clearTimeout(timer)
      break
    } catch (err) {
      window.clearTimeout(timer)
      if (attempt < MAX_RETRY) continue
      throw err
    }
  }

  if (res!.status === 401) {
    try {
      sessionStorage.removeItem('qq_token')
      sessionStorage.removeItem('qq_user')
    } catch {
      /* ignore */
    }
    redirectToLogin()
    throw new Error('未授权，请重新登录')
  }
  if (res!.status === 403) {
    // 已登录但权限不足 / 越权访问：保持登录态，仅向上抛出可识别的错误
    throw new ForbiddenError(await serverMessage(res!, '权限不足，无法访问该资源'))
  }
  if (res!.status === 429) {
    throw new Error('请求过于频繁，请稍后再试')
  }
  if (!res!.ok) throw new Error(await serverMessage(res!, `HTTP ${res!.status}`))
  return (await res!.json()) as T
}

/** 业务后端接口（:8080） */
export async function api<T = unknown>(path: string, opts: RequestOptions = {}): Promise<T> {
  try {
    return await raw<T>(API_BASE, path, opts)
  } catch (e) {
    // 权限不足必须暴露给调用方，绝不能用 mock 兜底伪装成"有数据"
    if (e instanceof ForbiddenError) throw e
    if (opts.mock !== undefined) {
      // 生产环境禁止 mock 降级：留痕并让请求如实失败，避免假数据伪装成成功
      if ((import.meta as any).env?.PROD) {
        console.error('[http] 生产环境禁止 mock 降级，已丢弃降级数据', { path })
      } else {
        return opts.mock as T
      }
    }
    throw e
  }
}

/**
 * 文件下载（CSV/Excel 等非 JSON 响应）
 * 复用同一套鉴权 + 签名头，拿到 Blob 后在浏览器触发另存为。
 */
export async function download(path: string, filename: string, opts: RequestOptions = {}): Promise<void> {
  const url = `${API_BASE}${path}${buildQuery(opts.query)}`
  const method = opts.method ?? 'GET'
  const headers: Record<string, string> = {}

  const token = getToken()
  if (token) headers['Authorization'] = `Bearer ${token}`
  if (!opts.skipSign) {
    const { buildSignHeaders, pathnameOf } = await import('../security/sign')
    Object.assign(headers, await buildSignHeaders(method, pathnameOf(`${API_BASE}${path}`)))
  }
  if (opts.body !== undefined) headers['Content-Type'] = 'application/json'

  const res = await fetch(url, {
    method,
    headers,
    body: opts.body !== undefined ? JSON.stringify(opts.body) : undefined,
  })

  if (res.status === 401) {
    try {
      sessionStorage.removeItem('qq_token')
      sessionStorage.removeItem('qq_user')
    } catch {
      /* ignore */
    }
    redirectToLogin()
    throw new Error('未授权，请重新登录')
  }
  if (res.status === 403) {
    throw new ForbiddenError(await serverMessage(res, '权限不足，无法导出'))
  }
  if (!res.ok) throw new Error(await serverMessage(res, `导出失败 HTTP ${res.status}`))

  const blob = await res.blob()
  const href = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = href
  a.download = filename
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  setTimeout(() => URL.revokeObjectURL(href), 2000)
}

/** Python AI 服务接口（:8090） */
export async function ai<T = unknown>(path: string, opts: RequestOptions = {}): Promise<T> {
  try {
    return await raw<T>(AI_BASE, path, opts)
  } catch (e) {
    if (e instanceof ForbiddenError) throw e
    if (opts.mock !== undefined) {
      if ((import.meta as any).env?.PROD) {
        console.error('[http] 生产环境禁止 mock 降级，已丢弃降级数据', { path })
      } else {
        return opts.mock as T
      }
    }
    throw e
  }
}
