/**
 * app store — 跨页全局 UI 状态：
 *  - 外观主题（theme）：持久化到 localStorage，并写入 <html data-theme>
 *  - 全局字体大小（fontScale）：通过根字号 CSS 变量 --qq-font-scale 承载，仅缩放文字、不改布局
 *  - 侧栏折叠态（用于后续响应式扩展）
 */
import { defineStore } from 'pinia'
import { DEFAULT_THEME } from '@/constants/theme'
import { updateSetting } from '@/api'

const STORAGE_KEY = 'qq_theme'
const FONT_KEY = 'qq_font_scale'
const DEFAULT_FONT_SCALE = 1
const FONT_SCALE_MIN = 0.7
const FONT_SCALE_MAX = 1.6

function readTheme(): string {
  try {
    return localStorage.getItem(STORAGE_KEY) ?? DEFAULT_THEME
  } catch {
    return DEFAULT_THEME
  }
}

function applyTheme(theme: string) {
  if (typeof document !== 'undefined') {
    document.documentElement.dataset.theme = theme
  }
}

/** 全局字体大小用根字号变量 --qq-font-scale 承载：所有文字用 rem 书写，
 *  只改这一个 CSS 变量即可整体缩放文字，且触发的是文本重排（开销远低于 zoom 的整页重光栅化），
 *  配合 rAF 节流，拖动滑块流畅无卡顿。 */
function readFontScale(): number {
  try {
    const v = parseFloat(localStorage.getItem(FONT_KEY) ?? '')
    if (!Number.isNaN(v) && v >= FONT_SCALE_MIN && v <= FONT_SCALE_MAX) return v
  } catch {
    /* ignore */
  }
  return DEFAULT_FONT_SCALE
}

let rafId = 0
function applyFontScale(scale: number) {
  if (typeof document === 'undefined') return
  if (rafId) cancelAnimationFrame(rafId)
  // 用 rAF 节流：拖动期间每帧最多只改一个 CSS 变量，避免连续 pointermove 堆积导致卡顿
  rafId = requestAnimationFrame(() => {
    document.documentElement.style.setProperty('--qq-font-scale', String(scale))
    rafId = 0
  })
}

export const useAppStore = defineStore('app', {
  state: () => ({
    theme: readTheme(),
    fontScale: readFontScale(),
    sidebarCollapsed: false,
  }),
  actions: {
    initTheme() {
      applyTheme(this.theme)
    },
    initFontScale() {
      applyFontScale(this.fontScale)
    },
    /** scale: 0.7~1.6；syncBackend=true（松手时）才持久化到本地与后端设置（ui.fontScale），
     *  拖动过程中只实时改 CSS 变量，避免频繁写存储造成卡顿。 */
    async setFontScale(scale: number, syncBackend = false) {
      const next = Math.min(FONT_SCALE_MAX, Math.max(FONT_SCALE_MIN, scale))
      this.fontScale = next
      applyFontScale(next) // 仅改根字号变量（rAF 节流），实时流畅预览
      if (syncBackend) {
        // 松手即落盘 + 跨设备一致：同步持久化到后端设置；无权限/无后端时静默保留本地值
        try {
          localStorage.setItem(FONT_KEY, String(next))
        } catch {
          /* ignore */
        }
        try {
          await updateSetting('ui.fontScale', String(next))
        } catch {
          /* 非管理员或无后端时忽略，本地缩放仍生效 */
        }
      }
    },
    async setTheme(theme: string) {
      this.theme = theme
      applyTheme(theme)
      try {
        localStorage.setItem(STORAGE_KEY, theme)
      } catch {
        /* ignore */
      }
      // 跨设备一致：同步持久化到后端设置（ui.theme）；无权限时静默保留本地主题
      try {
        await updateSetting('ui.theme', theme)
      } catch {
        /* 非管理员或无后端时忽略，本地主题仍生效 */
      }
    },
    toggleSidebar() {
      this.sidebarCollapsed = !this.sidebarCollapsed
    },
  },
})
