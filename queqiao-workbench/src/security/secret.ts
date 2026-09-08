// 前端请求签名密钥：须与后端 queqiao.sign-secret 一致（默认 queqiao@2026#sec）
// 后端默认来源：application-prod.properties 的 queqiao.sign-secret（可由环境变量 QQ_SIGN_SECRET 覆盖）
// 若部署时改了后端 QQ_SIGN_SECRET，请同步设置前端环境变量 VITE_SIGN_SECRET。
export const CLIENT_SIGN_SECRET =
  (import.meta.env.VITE_SIGN_SECRET as string | undefined) || 'queqiao@2026#sec'

// 调用方标识（仅作非空校验，后端不校验具体值）
export const CLIENT_SIGN_APP = 'queqiao-workbench'
