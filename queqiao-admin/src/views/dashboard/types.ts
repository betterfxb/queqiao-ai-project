/** Dashboard 页面内部数据模型（仅本功能域使用） */

export interface Kpi {
  label: string
  value: string
  unit?: string
  trend?: string
  up?: boolean
  icon?: string
  tone?: 'gold' | 'burgundy' | 'warning' | 'success'
}

/** 牵线看板中的一张匹配卡片 */
export interface MatchCard {
  male: string
  female: string
  maleInfo: string
  femaleInfo: string
  score: number
  tag: string
  tagKind: 'gold' | 'burgundy'
  agent: string
  time: string
}

export interface KanbanColumn {
  title: string
  count: number
  cards: MatchCard[]
}

export interface TodoItem {
  text: string
  priority: 'high' | 'medium' | 'low'
}

/** AI 最优匹配推荐 */
export interface MatchRec {
  name: string
  score: number
  detail: string
  reason: string
  avatarBg: string
  initial: string
}
