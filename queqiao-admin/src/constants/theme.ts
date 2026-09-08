/**
 * 外观主题选项（原内联于 Settings.vue）
 * key 同时作为 <html data-theme="..."> 的值，供 CSS 主题变量后续扩展。
 */
export interface ThemeOption {
  key: string
  name: string
  colors: [string, string, string]
}

export const THEMES: ThemeOption[] = [
  { key: 'burgundy', name: '经典勃艮第', colors: ['#7A2135', '#C9A96E', '#F8F5F0'] },
  { key: 'mint', name: '清新薄荷', colors: ['#5B8C7A', '#A8D5BA', '#F5FAF7'] },
  { key: 'navy', name: '深邃蓝金', colors: ['#1E2A4A', '#C9A96E', '#F0F2F8'] },
]

export const DEFAULT_THEME = 'burgundy'
