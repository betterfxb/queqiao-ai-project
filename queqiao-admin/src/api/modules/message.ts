import { api } from '../http'
import type { Conversation } from '../types'

const unwrap = <T>(r: T[] | { list?: T[] }): T[] =>
  Array.isArray(r) ? r : ((r?.list ?? []) as T[])

export interface ChatMsg {
  id?: string
  conversationId?: string
  sender: 'agent' | 'member' | 'system'
  content: string
  type?: string
  readFlag?: boolean
  createdAt?: string
}

export interface ConversationDetail extends Conversation {
  memberId?: string
  agent?: string
  messages?: ChatMsg[]
}

/** 会话列表 */
export const fetchConversations = (kind?: string, keyword?: string) =>
  api<Conversation[]>('/conversations', {
    query: { kind, keyword } as Record<string, string | undefined>,
  }).then(unwrap)

/** 会话详情 + 消息记录 */
export const fetchConversation = (id: string) =>
  api<ConversationDetail>('/conversations/' + id)

/** 发送消息 */
export const sendMessage = (id: string, content: string, type = 'text') =>
  api<ChatMsg>('/conversations/' + id + '/messages', { method: 'POST', body: { content, type } })

/** AI 回复建议 */
export const replySuggest = (id: string) =>
  api<{ source: string; suggestions: string[]; intent?: string; sentiment?: string }>(
    '/conversations/' + id + '/reply-suggest',
    { method: 'POST' },
  )

/** 从会员发起新会话 */
export const createConversation = (memberId: string) =>
  api<Conversation>('/conversations', { method: 'POST', body: { memberId } })

export const deleteConversation = (id: string) =>
  api('/conversations/' + id, { method: 'DELETE' })
