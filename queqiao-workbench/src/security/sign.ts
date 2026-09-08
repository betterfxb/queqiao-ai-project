import CryptoJS from 'crypto-js'
import { CLIENT_SIGN_SECRET, CLIENT_SIGN_APP } from './secret'

/**
 * 生成请求签名头，与后端 SecurityFilter.verifySignature 严格对齐：
 *   base = METHOD|uri|ts|nonce   （uri 为不含 query 的相对路径，与后端 getRequestURI() 一致；不含请求体）
 *   sign = HmacSHA256(base, secret) 的十六进制小写
 * 头字段：X-App / X-Ts(毫秒时间戳) / X-Nonce(随机串) / X-Sign
 *
 * 后端校验窗口：|now - ts| <= 5min，且 nonce 5min 内不可重放；
 * 白名单接口（如 /api/auth/login）免签名，但带正确签名也无副作用。
 */
export function buildSignHeaders(method: string, uri: string): Record<string, string> {
  const ts = String(Date.now())
  const nonce = Date.now().toString(36) + Math.random().toString(36).slice(2, 10)
  const base = `${method.toUpperCase()}|${uri}|${ts}|${nonce}`
  const sign = CryptoJS.HmacSHA256(base, CLIENT_SIGN_SECRET).toString(CryptoJS.enc.Hex)
  return {
    'X-App': CLIENT_SIGN_APP,
    'X-Ts': ts,
    'X-Nonce': nonce,
    'X-Sign': sign,
  }
}
