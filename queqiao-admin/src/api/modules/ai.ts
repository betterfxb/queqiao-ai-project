import { ai } from '../http'

/** AI 文本解析（Python :8090，已联通） */
export const aiParse = (text: string) =>
  ai('/parse', { method: 'POST', body: { text } })

/** AI 智能匹配（Python :8090，已联通） */
export const aiMatch = (target: unknown, candidates: unknown[]) =>
  ai('/match', { method: 'POST', body: { target, candidates } })
