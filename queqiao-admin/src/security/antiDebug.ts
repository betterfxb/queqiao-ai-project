/**
 * 生产环境轻量反调试 / 反篡改威慑
 *
 * 说明：这属于“提高攻击成本”的客户端威慑，并非真正的系统安全边界
 * （真正的边界在服务端 SecurityFilter / WAF / 网关）。作用：
 *  - 禁用右键菜单、F12 / Ctrl+Shift+I / 查看源代码 等常见打开 DevTools 的入口
 *  - 周期性探测 DevTools 是否打开（基于 debugger 时间差），打开则做降级处理
 * 仅在 import.meta.env.PROD 生效，开发态完全放开，不影响调试。
 */
export function installAntiDebug() {
  if (!(import.meta as any).env?.PROD) return

  const block = (e: Event) => e.preventDefault()

  document.addEventListener('contextmenu', block)
  document.addEventListener('selectstart', block)
  document.addEventListener('copy', block)
  document.addEventListener('cut', block)
  document.addEventListener('keydown', (e: KeyboardEvent) => {
    const k = e.key.toLowerCase()
    if (
      e.key === 'F12' ||
      (e.ctrlKey && e.shiftKey && (k === 'i' || k === 'j' || k === 'c')) ||
      (e.ctrlKey && k === 'u')
    ) {
      e.preventDefault()
    }
  })

  // DevTools 打开探测：debugger 在打开控制台时会显著拖慢，借此判断
  let threshold = 160
  setInterval(() => {
    const start = performance.now()
    // eslint-disable-next-line no-debugger
    debugger
    if (performance.now() - start > threshold) {
      // 已打开 DevTools：做温和降级（不破坏业务，仅做提示与停止敏感渲染）
      document.body.style.filter = 'blur(6px)'
      const tip = document.getElementById('devtools-tip')
      if (!tip) {
        const el = document.createElement('div')
        el.id = 'devtools-tip'
        el.textContent = '出于安全与合规考虑，调试控制台已禁用'
        el.style.cssText =
          'position:fixed;left:50%;top:20px;transform:translateX(-50%);z-index:99999;background:#7a2135;color:#fff;padding:10px 18px;border-radius:10px;font-size:13px;box-shadow:0 8px 24px rgba(0,0,0,.25)'
        document.body.appendChild(el)
      }
    }
  }, 1000)
}
