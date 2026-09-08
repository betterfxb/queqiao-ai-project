/** 会员管理页内部数据模型 */

/** 筛选 chips 分组（每组单选） */
export interface ChipGroup {
  key: string
  label: string
  options: string[]
  active: string
}

/** 列表 / 卡片视图切换 */
export type MemberView = 'list' | 'grid'

/** 高级筛选面板提交的条件（字段与后端 /api/members 查询参数一一对应） */
export interface AdvancedFilter {
  height: [number, number]
  income: string
  marriage: string
  house: string
  car: string
}
