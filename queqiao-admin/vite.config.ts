import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

// 统一安全响应头（dev / preview 服务器注入；生产部署时由网关 / Nginx 同样注入）
const SECURITY_HEADERS = {
  // 内容安全策略：默认仅同源；脚本/样式允许内联（Element Plus 与构建产物需要），
  // 禁止任意外部源脚本，显著收敛 XSS 面。生产可进一步收紧并启用 nonce。
  'Content-Security-Policy':
    "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; font-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'",
  'X-Frame-Options': 'DENY',
  'X-Content-Type-Options': 'nosniff',
  'Referrer-Policy': 'no-referrer-when-downgrade',
  'Permissions-Policy': 'camera=(), microphone=(), geolocation=(), interest-cohort=()',
  'Cross-Origin-Opener-Policy': 'same-origin',
  'Cross-Origin-Resource-Policy': 'same-origin',
  'X-XSS-Protection': '1; mode=block',
}

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  // 默认根路径部署；子路径部署时通过 VITE_PUBLIC_PATH=/app2/ 注入。
  const publicPath = env.VITE_PUBLIC_PATH || '/'

  return {
  base: publicPath,
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    host: '0.0.0.0',
    port: 5200,
    headers: SECURITY_HEADERS,
    // 预留后端 / Python AI 服务代理（前端模块先行，接口已打通）
    proxy: {
      '/api': {
        target: 'http://localhost:8082',
        changeOrigin: true,
      },
      '/ai': {
        target: 'http://localhost:8090',
        changeOrigin: true,
        rewrite: (p) => p.replace(/^\/ai/, ''),
      },
    },
  },
  preview: {
    host: '0.0.0.0',
    port: 5270,
    headers: SECURITY_HEADERS,
  },
  build: {
    outDir: 'dist',
    sourcemap: false,
    chunkSizeWarningLimit: 1200,
    // 工程化分包：把体积大的第三方库从业务代码中拆出，提升缓存命中与首屏加载
    rollupOptions: {
      output: {
        manualChunks: {
          vue: ['vue', 'vue-router', 'pinia'],
          echarts: ['echarts'],
          'element-plus': ['element-plus', '@element-plus/icons-vue'],
        },
      },
    },
  },
  }
})
