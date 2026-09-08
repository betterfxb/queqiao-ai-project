/**
 * 系统设置 · 静态配置（原内联于 Settings.vue）
 * 与各 section 组件就近共存，避免模板中散落大段字面量。
 */

/* ---------- 设置子导航 ---------- */
export interface SettingsNavItem {
  key: string
  label: string
  icon: string
}
export interface SettingsNavGroup {
  title: string
  items: SettingsNavItem[]
}

/** 仅保留页面中真实存在的锚点分区，避免出现点击无反应的死链 */
export const SETTINGS_NAV: SettingsNavGroup[] = [
  {
    title: '基础设置',
    items: [
      { key: 'profile', label: '机构信息', icon: 'user' },
      { key: 'matchRule', label: '业务规则', icon: 'link' },
    ],
  },
  {
    title: '提醒与智能',
    items: [{ key: 'notify', label: '通知与 AI', icon: 'bell' }],
  },
  {
    title: '系统',
    items: [
      { key: 'theme', label: '外观主题', icon: 'palette' },
      { key: 'security', label: '安全设置', icon: 'lock' },
      { key: 'log', label: '操作日志', icon: 'file-text' },
    ],
  },
]
