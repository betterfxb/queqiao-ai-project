/**
 * 实时通道客户端（原生 STOMP over WebSocket，零第三方依赖）。
 *
 * 连接后端 /ws/stomp（小程序同款原生端点；浏览器亦可用，无需引入 SockJS）。
 * 职责：
 *  - 携带 JWT 完成 STOMP CONNECT 鉴权（令牌放进 CONNECT 帧头 Authorization）；
 *  - 订阅两类主题：
 *      /topic/presence              红娘在线状态变化（看板实时刷新）
 *      /topic/conversation/{id}     会话新消息实时推送（发送方落库后由后端广播）
 *  - 断线指数退避自动重连，连接状态对外暴露；
 *  - 鉴权失败（令牌失效）自动断开并尝试重新拉取令牌。
 *
 * 与后端契约见《小程序实时对接契约.md》。
 */

const NULL = '\u0000'

export interface PresencePayload {
  agent: string
  online: boolean
  sessionId?: string
  at?: number
  onlineAgents: string[]
  count: number
}

export interface ConversationMessagePayload {
  type: 'message'
  conversationId: string
  sender: string
  message: Record<string, unknown>
  moderation?: Record<string, unknown>
}

type Status = 'idle' | 'connecting' | 'open' | 'closed' | 'error'

function getToken(): string {
  try {
    return sessionStorage.getItem('qq_token') ?? ''
  } catch {
    return ''
  }
}

function wsEndpoint(): string {
  // 与 HTTP 同源：VITE_API_BASE 可能是 /api 或完整地址，统一落到当前 host 的 /ws/stomp
  const proto = location.protocol === 'https:' ? 'wss:' : 'ws:'
  const host = (import.meta as any).env?.VITE_WS_HOST || location.host
  return `${proto}//${host}/ws/stomp`
}

export class RealtimeClient {
  private ws: WebSocket | null = null
  private endpoint = wsEndpoint()
  private reconnectAttempts = 0
  private readonly maxReconnect = 6
  private manualClose = false
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null
  private subSeq = 0

  private presenceCbs = new Set<(p: PresencePayload) => void>()
  private statusCbs = new Set<(s: Status) => void>()
  private convCbs = new Map<string, Set<(m: ConversationMessagePayload) => void>>()
  private meetingCbs = new Map<string, Set<(p: unknown) => void>>()
  private subscribed = new Set<string>() // 已发起订阅的目的地（重连后需恢复）

  status: Status = 'idle'

  connect(): void {
    if (this.ws && (this.ws.readyState === WebSocket.OPEN || this.ws.readyState === WebSocket.CONNECTING)) return
    const token = getToken()
    if (!token) {
      this.setStatus('error')
      return
    }
    this.manualClose = false
    this.setStatus('connecting')
    let ws: WebSocket
    try {
      ws = new WebSocket(this.endpoint)
    } catch (e) {
      this.setStatus('error')
      this.scheduleReconnect()
      return
    }
    this.ws = ws
    let buf = ''

    ws.onopen = () => {
      this.reconnectAttempts = 0
      ws.send(
        `CONNECT\naccept-version:1.2\nhost:${location.host}\nheart-beat:0,0\nAuthorization:Bearer ${token}\n\n${NULL}`,
      )
    }

    ws.onmessage = (ev: MessageEvent) => {
      buf += typeof ev.data === 'string' ? ev.data : ''
      let idx: number
      while ((idx = buf.indexOf(NULL)) >= 0) {
        const frame = buf.slice(0, idx)
        buf = buf.slice(idx + 1)
        if (frame) this.handleFrame(frame)
      }
    }

    ws.onclose = () => {
      this.setStatus('closed')
      if (!this.manualClose) this.scheduleReconnect()
    }

    ws.onerror = () => {
      this.setStatus('error')
      // onclose 会随后触发，由它决定是否重连
    }
  }

  disconnect(): void {
    this.manualClose = true
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer)
      this.reconnectTimer = null
    }
    try {
      this.ws?.close()
    } catch {
      /* ignore */
    }
    this.ws = null
  }

  /** 红娘在线状态回调（返回取消函数） */
  onPresence(cb: (p: PresencePayload) => void): () => void {
    this.presenceCbs.add(cb)
    return () => this.presenceCbs.delete(cb)
  }

  /** 连接状态回调 */
  onStatus(cb: (s: Status) => void): () => void {
    this.statusCbs.add(cb)
    cb(this.status)
    return () => this.statusCbs.delete(cb)
  }

  /** 订阅某会话的新消息，返回取消订阅函数 */
  subscribeConversation(id: string, cb: (m: ConversationMessagePayload) => void): () => void {
    const dest = `/topic/conversation/${id}`
    let set = this.convCbs.get(dest)
    if (!set) {
      set = new Set()
      this.convCbs.set(dest, set)
    }
    set.add(cb)
    this.subscribed.add(dest)
    this.sendSubscribe(dest)
    return () => {
      set!.delete(cb)
      if (set!.size === 0) {
        this.convCbs.delete(dest)
        this.subscribed.delete(dest)
        this.sendUnsubscribe(dest)
      }
    }
  }

  /** 订阅任意 /topic 目的地（如视频房间 /topic/meeting/{roomId}），返回取消订阅函数 */
  subscribe(dest: string, cb: (p: unknown) => void): () => void {
    let set = this.meetingCbs.get(dest)
    if (!set) {
      set = new Set()
      this.meetingCbs.set(dest, set)
    }
    set.add(cb)
    this.subscribed.add(dest)
    this.sendSubscribe(dest)
    return () => {
      set!.delete(cb)
      if (set!.size === 0) {
        this.meetingCbs.delete(dest)
        this.subscribed.delete(dest)
        this.sendUnsubscribe(dest)
      }
    }
  }

  // ---- 内部实现 ----

  private setStatus(s: Status) {
    this.status = s
    this.statusCbs.forEach((cb) => cb(s))
  }

  private scheduleReconnect() {
    if (this.reconnectAttempts >= this.maxReconnect) return
    const delay = Math.min(1000 * 2 ** this.reconnectAttempts, 15000)
    this.reconnectAttempts++
    this.reconnectTimer = setTimeout(() => this.connect(), delay)
  }

  private handleFrame(frame: string) {
    const lines = frame.split('\n')
    const cmd = lines[0]
    if (cmd === 'CONNECTED') {
      this.setStatus('open')
      // 重连后恢复已有订阅
      this.subscribed.forEach((d) => this.sendSubscribe(d))
      return
    }
    if (cmd === 'ERROR') {
      this.setStatus('error')
      // 鉴权类错误：断开，交由上层重新登录
      this.disconnect()
      return
    }
    if (cmd === 'MESSAGE') {
      let dest = ''
      let body = ''
      for (let i = 1; i < lines.length; i++) {
        if (lines[i] === '') {
          body = lines.slice(i + 1).join('\n')
          break
        }
        const c = lines[i].indexOf(':')
        if (c > 0 && lines[i].slice(0, c) === 'destination') dest = lines[i].slice(c + 1)
      }
      this.dispatch(dest, body)
    }
  }

  private dispatch(dest: string, body: string) {
    let payload: unknown = null
    try {
      payload = JSON.parse(body)
    } catch {
      return
    }
    if (dest === '/topic/presence') {
      this.presenceCbs.forEach((cb) => cb(payload as PresencePayload))
    } else if (dest.startsWith('/topic/conversation/')) {
      const set = this.convCbs.get(dest)
      if (set) set.forEach((cb) => cb(payload as ConversationMessagePayload))
    } else if (dest.startsWith('/topic/meeting/')) {
      const set = this.meetingCbs.get(dest)
      if (set) set.forEach((cb) => cb(payload))
    }
  }

  private sendSubscribe(dest: string) {
    if (!this.ws || this.ws.readyState !== WebSocket.OPEN) return
    const id = 's' + ++this.subSeq
    this.ws.send(`SUBSCRIBE\nid:${id}\ndestination:${dest}\n\n${NULL}`)
  }

  private sendUnsubscribe(dest: string) {
    if (!this.ws || this.ws.readyState !== WebSocket.OPEN) return
    this.ws.send(`UNSUBSCRIBE\nid:${dest}\n\n${NULL}`)
  }
}

/** 全局单例，供 Pinia store / 组件复用同一连接 */
export const realtime = new RealtimeClient()
