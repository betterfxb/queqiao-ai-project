/**
 * 请求签名工具（HMAC-SHA256，Web Crypto）
 *
 * 与服务端 SecurityFilter 约定的签名基底完全一致：
 *   sign = HMAC_SHA256( secret , `${method}|${pathname}|${ts}|${nonce}` )
 * 头部附带：X-App / X-Ts / X-Nonce / X-Sign
 *  - 服务端校验时间戳漂移（±5min）与 nonce 一次性 → 防重放
 *  - 校验签名 → 防篡改 / 防伪造请求
 */
import { CLIENT_SIGN_SECRET } from './secret'

function toHex(buf: ArrayBuffer): string {
  return Array.from(new Uint8Array(buf))
    .map((b) => b.toString(16).padStart(2, '0'))
    .join('')
}

async function hmacSha256Hex(message: string, secret: string): Promise<string> {
  const enc = new TextEncoder()
  const key = await crypto.subtle.importKey(
    'raw',
    enc.encode(secret),
    { name: 'HMAC', hash: 'SHA-256' },
    false,
    ['sign'],
  )
  const sig = await crypto.subtle.sign('HMAC', key, enc.encode(message))
  return toHex(sig)
}

export interface SignHeaders {
  'X-App': string
  'X-Ts': string
  'X-Nonce': string
  'X-Sign': string
}

/** 生成签名头部。pathname 不含 query string。 */
export async function buildSignHeaders(
  method: string,
  pathname: string,
): Promise<SignHeaders> {
  const ts = Date.now()
  const nonce =
    (crypto as any).randomUUID?.() ??
    `${ts}-${Math.random().toString(36).slice(2)}`
  const base = `${method.toUpperCase()}|${pathname}|${ts}|${nonce}`
  const sign = await hmacSha256Hex(base, CLIENT_SIGN_SECRET)
  return {
    'X-App': 'queqiao-admin',
    'X-Ts': String(ts),
    'X-Nonce': nonce,
    'X-Sign': sign,
  }
}

export function pathnameOf(url: string): string {
  // 去掉 origin 与 query
  try {
    const u = new URL(url, location.origin)
    return u.pathname
  } catch {
    return url.split('?')[0]
  }
}
