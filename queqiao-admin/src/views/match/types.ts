/** 牵线详情页内部数据模型 */

export interface PersonField {
  label: string
  value: string
}

export interface PersonResponse {
  text: string
  /** 决定回应条的配色 */
  kind: 'success' | 'warning'
  time: string
}

/** 牵线双方中的一方 */
export interface PairPerson {
  id: string
  name: string
  age: number
  level: string
  avatarBg: string
  grid: PersonField[]
  traits: string[]
  bio: string
  response: PersonResponse
}

/** 牵线流程步骤 */
export interface MatchStep {
  label: string
  state: 'done' | 'active' | 'pending'
}

/** AI 匹配分析维度条 */
export interface AiBar {
  label: string
  pct: number
}

/** 牵线进展时间线条目 */
export interface ProgressItem {
  text: string
  time: string
  color: string
}

/** 牵线阶段（与后端 Pair.stage 取值一致，顺序即推进顺序） */
export const STAGES = ['初步推荐', '双方沟通', '安排见面', '持续跟进'] as const

/** 由后端 Member 构造资料卡数据 */
export const toPairPerson = (m: any, response: PersonResponse): PairPerson => ({
  id: m?.id ?? '',
  name: m?.name ?? '—',
  age: Number(m?.age ?? 0),
  level: m?.level ?? '普通',
  avatarBg: m?.avatarBg ?? (m?.gender === '男' ? '#4f6d8f' : '#a8607a'),
  grid: [
    { label: '身高', value: m?.height ? `${m.height}cm` : '—' },
    { label: '学历', value: m?.education ?? '—' },
    { label: '职业', value: m?.job ?? '—' },
    { label: '年收入', value: m?.income ?? '—' },
    { label: '城市', value: m?.city ?? '—' },
    { label: '房产', value: m?.house ?? '—' },
    { label: '婚姻', value: m?.marriage ?? '—' },
    { label: '购车', value: m?.car ?? '—' },
  ],
  traits: String(m?.tags ?? '').split(/[,，、]/).map((s: string) => s.trim()).filter(Boolean),
  bio: m?.bio ?? m?.requirement ?? '暂无自我介绍',
  response,
})
