import { defineConfig } from 'vite'
import uniPlugin from '@dcloudio/vite-plugin-uni'
import UnoCSS from 'unocss/vite'

// @dcloudio/vite-plugin-uni 为 CJS 包，ESM 配置下需兼容 default 导出
const uni = (uniPlugin as any).default ?? uniPlugin

export default defineConfig({
  // 子路径部署支持：VITE_PUBLIC_PATH=/app2/ npm run build:h5
  // 不设置时默认根路径 '/'，资源引用 /assets/...，由 nginx 反代托管。
  base: (process.env.VITE_PUBLIC_PATH as string) || '/',
  plugins: [uni(), UnoCSS()],
  server: {
    host: '0.0.0.0',
    port: 5173,
    strictPort: false,
    // 开发期把后端/AI 服务代理到同源，彻底规避浏览器 CORS 限制。
    // 原因：后端 CORS 是严格白名单（只认 http://localhost:5173），
    // 一旦用 127.0.0.1 访问、或从预览面板/iframe（Origin: null）打开，
    // 预检会被 403 Invalid CORS request 拒绝，表现为「登录后所有接口都失败」。
    // 代理后请求同源发出，不再触发跨域预检；生产/真机打包不受影响。
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/ai': {
        target: 'http://localhost:8090',
        changeOrigin: true,
      },
    },
    watch: {
      // 忽略构建产物目录：否则 dev server 会监听 dist / dist_old_* 的写入，
      // 每次执行 build 都会触发无意义的 HMR page reload。
      ignored: ['**/dist/**', '**/dist_old_*/**', '**/dist.bak.*/**', '**/.git/**', '**/node_modules/**'],
    },
  },
})
