/**
 * 牵线状态机工具 — 与后端 PairStatus.canTransitTo 严格对齐
 *
 * 关键约定：
 *  - 前端「完成/已脱单」对应后端枚举「已成功」。
 *  - 后端 MatchController 在 status == "已成功" 时才触发分佣计提 + 成功积分，
 *    因此「完成」动作必须发送 wire 值「已成功」，否则业务副作用永不触发（历史 bug）。
 *  - 读取后端状态时，把 wire「已成功」归一显示为「已脱单」，保持前端 UI 一致。
 */

export type PairAction = 'call' | 'schedule' | 'done' | 'fail'

// 显示态 -> 后端 wire 值
export const WIRE: Record<string, string> = {
  待确认: '待确认',
  牵线中: '牵线中',
  已约见: '已约见',
  已脱单: '已成功',
  已成功: '已成功',
  已终止: '已终止',
}

// 后端 wire -> 显示态
export const DISPLAY: Record<string, string> = {
  待确认: '待确认',
  牵线中: '牵线中',
  已约见: '已约见',
  已成功: '已脱单',
  已脱单: '已脱单',
  已终止: '已终止',
}

// 合法流转（对齐后端 PairStatus.canTransitTo），全部使用 wire 值
const ALLOWED_WIRE: Record<string, string[]> = {
  待确认: ['牵线中', '双方同意', '已终止'],
  牵线中: ['双方同意', '已约见', '已终止'],
  进行中: ['双方同意', '已约见', '已终止'],
  双方同意: ['已约见', '已终止'],
  已约见: ['已成功', '已终止'],
  已成功: [],
  已终止: [],
}

// 动作 -> 目标 wire 值
const ACTION_WIRE: Record<PairAction, string> = {
  call: '牵线中',
  schedule: '已约见',
  done: '已成功',
  fail: '已终止',
}

export function toWire(display: string): string {
  return WIRE[display] ?? display
}
export function toDisplay(wire: string): string {
  return DISPLAY[wire] ?? wire
}

/** 当前显示态下，某动作是否允许（用于前端禁用非法按钮） */
export function canAction(action: PairAction, currentDisplay: string): boolean {
  const cur = toWire(currentDisplay)
  const allowed = ALLOWED_WIRE[cur]
  if (!allowed) return false
  return allowed.includes(ACTION_WIRE[action])
}

/** 推荐下一步动作（用于高亮引导），优先顺序：跟进 → 约见 → 完成 */
export function nextAction(currentDisplay: string): PairAction | null {
  const order: PairAction[] = ['call', 'schedule', 'done']
  for (const a of order) if (canAction(a, currentDisplay)) return a
  return null
}

export const isTerminal = (display: string) =>
  toWire(display) === '已成功' || toWire(display) === '已终止'
