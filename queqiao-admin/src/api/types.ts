/** 会员 */
export interface Member {
  id: string
  name: string
  gender: '男' | '女'
  age: number
  city: string
  education: string
  job: string
  level: '普通' | 'VIP' | 'SVIP' | '钻石VIP' | '金卡'
  status: '牵线中' | '待审核' | '认证中' | '已认证' | '匹配中' | '已约见' | '已暂停'
  agent: string
  matchCount?: number
  successRate?: number
  lastActive?: string
  avatarBg?: string
  phone?: string
  /** 扩展档案字段（与后端 Member 实体一致） */
  height?: number
  income?: string
  marriage?: string
  hasChild?: string
  hometown?: string
  house?: string
  car?: string
  requirement?: string
  tags?: string
  bio?: string
  verified?: boolean
  createdAt?: string
  updatedAt?: string
}

/** 红娘 */
export interface Agent {
  id: string
  name: string
  level: '金牌红娘' | '资深红娘' | '普通红娘'
  status: '在职' | '休假' | '离职'
  memberCount: number
  monthMatch: number
  successRate: number
  monthRevenue: number
  rating: number
  avatarBg?: string
  phone?: string
  region?: string
  capacity?: number
  joinDate?: string
}

/** 牵线配对 */
export interface PairSide {
  name: string
  age: number
  level: string
  avatarBg?: string
  grid: { label: string; value: string }[]
  traits: string[]
  bio: string
  response: { text: string; kind: 'success' | 'warning'; time: string }
}

/** 审核事项（后端 AuditTask 归一化后的形状） */
export interface AuditItem {
  id: string
  type: '实名认证' | '资料修改' | '会员升级' | '活动报名'
  applicant: string
  gender: '男' | '女'
  age: number
  memberId: string
  agent: string
  urgency: '紧急' | '普通' | '低'
  timeAgo: string
  /** 待审核 / 已通过 / 已驳回 */
  status?: string
  submittedAt?: string
  handledAt?: string
  handler?: string
  remark?: string
  reason?: string
  change?: { field: string; from: string; to: string }
  pay?: { text: string; paid: boolean }
}

/** 活动 */
export interface Activity {
  id: string
  title: string
  date: string
  weekday: string
  time: string
  place: string
  status: '报名中' | '进行中' | '已满员' | '已结束' | '草稿'
  tags: string[]
  signed: number
  capacity: number
  female: number
  male: number
  price: number
  revenue: number
  expectedPairs: number
  agent: string
  hero?: boolean
}

/** 交易记录 */
export interface FinanceRecord {
  time: string
  member: string
  type: '会员续费' | '牵线服务' | '活动收费' | '退款'
  amount: number
  status: '已完成' | '处理中'
}

/** 报表导出记录 */
export interface ReportRecord {
  name: string
  category: '财务' | '红娘' | '会员' | '活动' | '牵线'
  format: 'Excel' | 'PDF' | 'CSV'
  range: string
  exportedAt: string
  operator: string
  status: '已完成' | '生成中'
  size: string
}

/** CRM 看板卡 */
export interface CrmCard {
  id: string
  name: string
  age: number
  gender: '男' | '女'
  level: '普通' | 'VIP' | 'SVIP'
  phone: string
  stayDays: number
  fresh: 'fresh' | 'warning' | 'danger'
  freshText: string
  nextAction: string
  star?: boolean
}

/** 会话 */
export interface Conversation {
  id: string
  name: string
  timeAgo: string
  preview: string
  unread: number
  kind: 'member' | 'system'
  online?: boolean
}
