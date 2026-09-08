/** 报表模板（页面本地类型，仅 reports 模块内使用） */
export interface ReportTemplate {
  /** 展示名称 */
  name: string
  /** 后端 category 取值：会员 / 财务 / 红娘 / 活动 / 牵线 */
  cat: string
  /** 说明文案 */
  desc: string
  /** 图标 */
  icon: string
  /** 支持的导出格式（后端统一产出 UTF-8 BOM 的 CSV，Excel 可直接打开） */
  formats: string[]
}

/** 与后端 ReportController#download 的 switch 分支一一对应 */
export const REPORT_TEMPLATES: ReportTemplate[] = [
  { name: '会员信息总表', cat: '会员', desc: '全部会员档案：性别/年龄/城市/学历/等级/归属红娘', icon: 'users', formats: ['CSV'] },
  { name: '财务流水明细', cat: '财务', desc: '全部订单：订单号/会员/类型/金额/状态/时间', icon: 'dollar', formats: ['CSV'] },
  { name: '红娘业绩排行', cat: '红娘', desc: '红娘等级/在职状态/负责会员/本月牵线/成功率/营收', icon: 'award', formats: ['CSV'] },
  { name: '活动效果分析', cat: '活动', desc: '活动日期/地点/状态/报名人数/容量/营收', icon: 'calendar-days', formats: ['CSV'] },
  { name: '牵线匹配统计', cat: '牵线', desc: '男女双方/匹配度/牵线状态/负责红娘/创建时间', icon: 'link', formats: ['CSV'] },
]
