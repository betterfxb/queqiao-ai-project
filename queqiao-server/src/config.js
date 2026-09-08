// 集中配置：全部来自环境变量，默认值面向本地开发（参见 .env.example）
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))

const toInt = (v, d) => {
  const n = Number.parseInt(v, 10)
  return Number.isFinite(n) ? n : d
}
const toBool = (v, d) => (v === undefined || v === '' ? d : /^(1|true|yes|on)$/i.test(String(v)))
const toList = (v, d) =>
  String(v ?? d)
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean)

const nodeEnv = process.env.NODE_ENV || 'development'

export const config = {
  nodeEnv,
  isProd: nodeEnv === 'production',
  isTest: nodeEnv === 'test',
  port: toInt(process.env.PORT, 8080),
  // 默认只监听回环地址，避免容器/主机上公网可达
  host: process.env.HOST || '127.0.0.1',
  dbFile: process.env.DB_FILE
    ? path.resolve(process.env.DB_FILE)
    : path.join(__dirname, '..', 'data', 'db.json'),
  saveDebounceMs: toInt(process.env.SAVE_DEBOUNCE_MS, 200),
  aiBase: process.env.AI_BASE || 'http://localhost:8090',
  aiTimeoutMs: toInt(process.env.AI_TIMEOUT_MS, 5000),
  apiToken: process.env.API_TOKEN || '',
  corsOrigins: toList(process.env.CORS_ORIGINS, 'http://localhost:5173,http://127.0.0.1:5173'),
  timezone: process.env.TZ_NAME || 'Asia/Shanghai',
  jsonLimit: process.env.JSON_LIMIT || '256kb',
  requestLog: toBool(process.env.REQUEST_LOG, nodeEnv !== 'test'),
}

// 未配置 API_TOKEN 时退化为「本地开发模式」：放行但打印告警；生产环境直接拒绝启动
export const authDisabled = !config.apiToken

export function assertProductionConfig() {
  if (config.isProd && authDisabled) {
    throw new Error('[config] 生产环境必须配置 API_TOKEN，否则接口将完全无鉴权')
  }
}
