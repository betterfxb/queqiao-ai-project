import { defineConfig, presetUno } from 'unocss'

/**
 * 鹊桥 · 红娘工作台 — UnoCSS 配置
 * 与 src/styles/tokens.scss 中的 CSS 变量保持一致：
 * 主色 #8B254A / 香槟金 #D4AF37 / 暖白 #FDFBF7 → 燕麦 #F5F0EB
 *
 * 说明：为兼容小程序端（不支持部分选择器），仅使用 class 型原子类，
 * 复杂毛玻璃/渐变效果统一走 tokens.scss 中的语义类（.glass-card 等）。
 */
export default defineConfig({
  presets: [presetUno()],
  theme: {
    colors: {
      primary: {
        DEFAULT: '#8B254A',
        light: '#A8456C',
        lighter: '#C97A96',
        soft: 'rgba(139, 37, 74, 0.08)',
      },
      gold: {
        DEFAULT: '#D4AF37',
        light: '#E3C766',
        dark: '#A8861E',
      },
      ink: {
        1: '#2E222A',
        2: '#5C4B55',
        3: '#968089',
        4: '#BFAAB2',
      },
      cream: {
        base: '#FDFBF7',
        oat: '#F5F0EB',
      },
      success: '#5B8C5A',
      warning: '#C89060',
      danger: '#B84A4A',
    },
  },
  shortcuts: {
    // 常用布局快捷方式
    'flex-center': 'flex items-center justify-center',
    'flex-between': 'flex items-center justify-between',
  },
})
