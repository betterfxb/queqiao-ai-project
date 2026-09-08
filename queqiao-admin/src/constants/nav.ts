/**
 * 运营中心主导航配置（原内联于 AppShell.vue）
 * 抽为常量，便于权限过滤、动态菜单、测试复用。
 */
export interface NavItem {
  key: string
  label: string
  icon: string
  path: string
  badge?: boolean
}

export const NAV_ITEMS: NavItem[] = [
  { key: 'dashboard', label: '仪表盘', icon: 'dashboard', path: '/' },
  { key: 'members', label: '会员管理', icon: 'users', path: '/members' },
  { key: 'agents', label: '红娘团队', icon: 'heart', path: '/agents' },
  { key: 'match', label: '牵线管理', icon: 'link', path: '/match' },
  { key: 'audit', label: '审核中心', icon: 'shield-check', path: '/audit' },
  { key: 'crm', label: '客户跟进', icon: 'target', path: '/crm' },
  { key: 'activities', label: '活动管理', icon: 'calendar', path: '/activities' },
  { key: 'calendar', label: '日程日历', icon: 'calendar-days', path: '/calendar' },
  { key: 'messages', label: '消息中心', icon: 'message', path: '/messages', badge: true },
  { key: 'finance', label: '财务中心', icon: 'dollar', path: '/finance' },
  { key: 'pay', label: '支付中心', icon: 'dollar', path: '/pay' },
  { key: 'commission', label: '佣金结算', icon: 'award', path: '/commission' },
  { key: 'trust', label: '信任合规', icon: 'shield', path: '/trust' },
  { key: 'products', label: '服务产品', icon: 'package', path: '/products' },
  { key: 'escrow', label: '资金存管', icon: 'shield', path: '/escrow' },
  { key: 'reports', label: '报表导出', icon: 'file-text', path: '/reports' },
  { key: 'smart-ops', label: '智能运营', icon: 'sparkles', path: '/smart-ops' },
  { key: 'growth', label: '增长中心', icon: 'trending', path: '/growth' },
  { key: 'points', label: '积分榜', icon: 'star', path: '/points' },
  { key: 'quiz', label: '情感测评', icon: 'sparkles', path: '/members/quiz' },
  { key: 'settings', label: '系统设置', icon: 'settings', path: '/settings' },
]
