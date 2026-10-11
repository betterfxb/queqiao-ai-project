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
  { key: 'burgundy', name: '经典勃艮第', colors: ['#7D2B3D', '#A8863F', '#F6F5F3'] },
  { key: 'mint', name: '沉静松绿', colors: ['#3F7261', '#A8863F', '#F4F6F4'] },
  { key: 'navy', name: '沉稳藏蓝', colors: ['#2F4160', '#A8863F', '#F4F5F7'] },
]

export const DEFAULT_THEME = 'burgundy'
