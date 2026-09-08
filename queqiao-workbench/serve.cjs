const http = require('http')
const fs = require('fs')
const path = require('path')

const ROOT = path.resolve(__dirname, 'dist/build/h5')
const PORT = Number(process.env.PREVIEW_PORT || 5191)
const HOST = '0.0.0.0'

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'application/javascript; charset=utf-8',
  '.mjs': 'application/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.svg': 'image/svg+xml',
  '.ico': 'image/x-icon',
  '.woff': 'font/woff',
  '.woff2': 'font/woff2',
  '.ttf': 'font/ttf',
}

const server = http.createServer((req, res) => {
  let urlPath = decodeURIComponent((req.url || '/').split('?')[0])
  if (urlPath === '/') urlPath = '/index.html'

  // 防目录穿越
  const safePath = path.normalize(path.join(ROOT, urlPath))
  if (!safePath.startsWith(ROOT)) {
    res.writeHead(403)
    return res.end('Forbidden')
  }

  fs.readFile(safePath, (err, data) => {
    if (err) {
      // SPA 兜底：未知路径回退 index.html
      fs.readFile(path.join(ROOT, 'index.html'), (e2, html) => {
        if (e2) {
          res.writeHead(404)
          return res.end('Not Found')
        }
        res.writeHead(200, { 'Content-Type': MIME['.html'] })
        res.end(html)
      })
      return
    }
    const ext = path.extname(safePath).toLowerCase()
    res.writeHead(200, { 'Content-Type': MIME[ext] || 'application/octet-stream' })
    res.end(data)
  })
})

server.listen(PORT, HOST, () => {
  console.log(`[preview] H5 serving on http://${HOST}:${PORT} (root: ${ROOT})`)
})
