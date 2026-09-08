/**
 * 实时状态（Pinia）
 * 统一管理与后端的 WebSocket 长连接，向组件暴露「连接状态」与「红娘在线看板」。
 * 会话消息订阅由具体会话页按需调用 watchConversation。
 */
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { realtime, type PresencePayload, type ConversationMessagePayload } from '../api/ws'

export const useRealtimeStore = defineStore('realtime', () => {
  const status = ref<'idle' | 'connecting' | 'open' | 'closed' | 'error'>('idle')
  const onlineAgents = ref<string[]>([])
  const lastMessage = ref<ConversationMessagePayload | null>(null)

  let started = false

  function init() {
    if (started) return
    started = true
    realtime.onStatus((s) => (status.value = s))
    realtime.onPresence((p: PresencePayload) => {
      onlineAgents.value = p.onlineAgents ?? []
    })
    realtime.connect()
  }

  /** 订阅会话新消息，返回取消函数（组件 onUnmounted 时调用） */
  function watchConversation(id: string, cb: (m: ConversationMessagePayload) => void): () => void {
    lastMessage.value = null
    return realtime.subscribeConversation(id, (m) => {
      lastMessage.value = m
      cb(m)
    })
  }

  function stop() {
    realtime.disconnect()
  }

  return { status, onlineAgents, lastMessage, init, watchConversation, stop }
})
