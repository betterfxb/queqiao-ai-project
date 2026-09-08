/**
 * 会员相关展示辅助函数（等级/状态/操作的样式映射）
 * 原为各页面内联逻辑，统一收敛到 utils 以便复用与单测。
 */
import type { Member } from '@/api/types'

/** 等级 → level-badge 样式类 */
export function levelClass(level: Member['level']): string {
  if (level === 'SVIP') return 'svip'
  if (level === 'VIP') return 'vip'
  if (level === '钻石VIP') return 'diamond'
  if (level === '金卡') return 'premium'
  return 'standard'
}

/** 状态 → status-pill 样式类 */
export function statusClass(status: Member['status']): string {
  if (status === '已认证' || status === '牵线中' || status === '已约见') return 'active'
  if (status === '待审核') return 'warning'
  if (status === '匹配中') return 'gold'
  return 'neutral'
}

/** 状态 → 行内主操作文案 */
export function rowAction(status: Member['status']): string {
  if (status === '待审核') return '审核通过'
  if (status === '匹配中') return '查看匹配'
  if (status === '已约见') return '记录反馈'
  return '安排牵线'
}

/** 匹配度 → 环形渐变背景（conic-gradient） */
export function matchRingBg(score: number): string {
  const deg = Math.max(0, Math.min(100, score)) * 3.6
  return `conic-gradient(var(--dt-gold) 0deg, var(--dt-gold) ${deg}deg, rgba(201,169,110,0.15) ${deg}deg)`
}
