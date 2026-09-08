// 鹊桥 · 红娘工作台 — 启动入口（监听与 app 定义分离，便于测试）
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import app from './app.js'
import { assertProductionConfig, authDisabled, config } from './config.js'
import { flush } from './store.js'

export function start() {
  assertProductionConfig()
  if (authDisabled) {
    console.warn('[auth] 未配置 API_TOKEN，当前为本地开发模式：所有请求视为已授权，请勿对外暴露')
  }
  const server = app.listen(config.port, config.host, () => {
    console.log(`[queqiao-server] listening on http://${config.host}:${config.port} (${config.nodeEnv})`)
  })

  const shutdown = (signal) => async () => {
    console.log(`[queqiao-server] 收到 ${signal}，正在关闭…`)
    server.close(async () => {
      await flush()
      process.exit(0)
    })
    setTimeout(() => process.exit(1), 5000).unref()
  }
  process.on('SIGTERM', shutdown('SIGTERM'))
  process.on('SIGINT', shutdown('SIGINT'))
  return server
}

// 兼容 `node src/index.js`、`node .`（argv[1] 为目录）与 `node src/index`
function isEntryModule() {
  if (!process.argv[1]) return false
  let target = path.resolve(process.argv[1])
  try {
    if (fs.statSync(target).isDirectory()) {
      const pkg = JSON.parse(fs.readFileSync(path.join(target, 'package.json'), 'utf-8'))
      target = path.resolve(target, pkg.main || 'index.js')
    }
  } catch {
    if (!path.extname(target)) target += '.js'
  }
  return target === path.resolve(fileURLToPath(import.meta.url))
}

if (isEntryModule()) start()

export { app }
export default app
