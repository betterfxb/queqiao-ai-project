import type { InjectionKey, ComputedRef, Ref } from 'vue'

export interface GrowthContext {
  isAdmin: ComputedRef<boolean>
  isManager: ComputedRef<boolean>
  isAgent: ComputedRef<boolean>
  /** 会员下拉数据源（登记转介绍/订阅社群等复用） */
  members: Ref<any[]>
  /** 牵线下拉数据源（生成案例/推荐商户复用） */
  pairs: Ref<any[]>
  memberOpts: ComputedRef<{ value: string; label: string }[]>
  pairOpts: ComputedRef<{ value: string; label: string }[]>
  /** 金额格式化：保留两位小数，空值回退 0.00 */
  fmt: (n?: number) => string
  /** 状态 -> el-tag 语义色 */
  statusType: (s: string) => 'success' | 'warning' | 'info' | 'danger'
}

export const GROWTH_KEY: InjectionKey<GrowthContext> = Symbol('growth-context')

import { inject } from 'vue'

/** 在增长中心任意子模块中安全获取共享上下文 */
export function useGrowth(): GrowthContext {
  const ctx = inject(GROWTH_KEY)
  if (!ctx) throw new Error('useGrowth() 必须在 GrowthCenter 内调用')
  return ctx
}
