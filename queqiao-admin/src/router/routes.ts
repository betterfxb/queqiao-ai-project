import type { RouteRecordRaw } from 'vue-router'

const AppShell = () => import('@/layout/AppShell.vue')

/**
 * 路由表（与守卫逻辑解耦，便于按需拆分/权限过滤）
 * 页面统一收敛到 views/<feature>/index.vue，复杂区块再下沉到 views/<feature>/components/。
 */
export const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录' },
  },
  {
    path: '/screen',
    name: 'screen',
    component: () => import('@/views/bigscreen/index.vue'),
    meta: { title: '数据大屏' },
  },
  {
    // 会员档案编辑：无侧边栏，独立顶栏
    path: '/members/edit/:id?',
    name: 'member-edit',
    component: () => import('@/views/member-edit/index.vue'),
    meta: { title: '会员档案编辑' },
  },
  {
    path: '/',
    component: AppShell,
    children: [
      { path: '', name: 'dashboard', component: () => import('@/views/dashboard/index.vue'), meta: { title: '牵线运营中心', nav: 'dashboard' } },
      { path: 'members', name: 'members', component: () => import('@/views/members/index.vue'), meta: { title: '会员管理', nav: 'members' } },
      { path: 'match/:id?', name: 'match', component: () => import('@/views/match/index.vue'), meta: { title: '牵线详情', nav: 'match' } },
      { path: 'audit', name: 'audit', component: () => import('@/views/audit/index.vue'), meta: { title: '审核工作台', nav: 'audit' } },
      { path: 'agents', name: 'agents', component: () => import('@/views/agents/index.vue'), meta: { title: '红娘团队管理', nav: 'agents' } },
      { path: 'crm', name: 'crm', component: () => import('@/views/crm/index.vue'), meta: { title: 'CRM 客户跟进', nav: 'crm' } },
      { path: 'calendar', name: 'calendar', component: () => import('@/views/calendar/index.vue'), meta: { title: '日程日历', nav: 'calendar' } },
      { path: 'activities', name: 'activities', component: () => import('@/views/activities/index.vue'), meta: { title: '活动管理', nav: 'activities' } },
      { path: 'messages', name: 'messages', component: () => import('@/views/messages/index.vue'), meta: { title: '消息中心', nav: 'messages' } },
      { path: 'finance', name: 'finance', component: () => import('@/views/finance/index.vue'), meta: { title: '财务报表', nav: 'finance' } },
      { path: 'pay', name: 'pay', component: () => import('@/views/pay/index.vue'), meta: { title: '支付中心', nav: 'pay' } },
      { path: 'commission', name: 'commission', component: () => import('@/views/commission/index.vue'), meta: { title: '佣金结算', nav: 'commission' } },
      { path: 'trust', name: 'trust', component: () => import('@/views/trust/index.vue'), meta: { title: '信任合规', nav: 'trust' } },
      { path: 'products', name: 'products', component: () => import('@/views/products/index.vue'), meta: { title: '服务产品', nav: 'products' } },
      { path: 'escrow', name: 'escrow', component: () => import('@/views/finance/Escrow.vue'), meta: { title: '资金存管', nav: 'escrow' } },
      { path: 'reports', name: 'reports', component: () => import('@/views/reports/index.vue'), meta: { title: '报表导出', nav: 'reports' } },
      { path: 'settings', name: 'settings', component: () => import('@/views/settings/index.vue'), meta: { title: '系统设置', nav: 'settings' } },
      { path: 'smart-ops', name: 'smart-ops', component: () => import('@/views/smart-ops/index.vue'), meta: { title: '智能运营中心', nav: 'smart-ops' } },
      { path: 'growth', name: 'growth', component: () => import('@/views/growth/index.vue'), meta: { title: '增长中心', nav: 'growth' } },
      { path: 'points', name: 'points', component: () => import('@/views/members/PointsBoard.vue'), meta: { title: '积分榜', nav: 'points' } },
      { path: 'members/quiz', name: 'member-quiz', component: () => import('@/views/members/Quiz.vue'), meta: { title: '情感测评', nav: 'quiz' } },
    ],
  },
  { path: '/:pathMatch(.*)*', redirect: '/' },
]
