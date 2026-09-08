/**
 * 客户端签名密钥（前端防篡改 / 防重放 之 请求签名）
 *
 * ⚠️ 生产环境注意：
 *  - 此处为原型占位密钥。真实环境必须改为：
 *    1) 通过非对称方案（前端用后端下发的临时 ticket 签名）；或
 *    2) 由登录接口下发 per-session 的 ephemeral key，存于内存（不落盘）；或
 *    3) 直接由网关 / WAF / BFF 层做签名与风控，前端只透传一次性 nonce。
 *  - 前端代码可被逆向，任何写死在 bundle 里的密钥都只是“提高攻击成本”，
 *    真正的强校验必须发生在服务端（见 SecurityFilter）。
 */
export const CLIENT_SIGN_SECRET = 'queqiao@2026#sec'
/** 允许的签名时间漂移窗口（毫秒），与服务端保持一致 */
export const SIGN_TTL_MS = 5 * 60 * 1000
