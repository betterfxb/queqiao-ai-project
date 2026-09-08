import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'

import App from './App.vue'
import router from './router'
import AppIcon from './components/AppIcon.vue'
import VChart from './components/VChart.vue'
import SliderCaptcha from './components/SliderCaptcha.vue'
// 全局注册的公共组件（跨页复用，无需逐页 import）
import GlassCard from './components/common/GlassCard.vue'
import SectionHeader from './components/common/SectionHeader.vue'
import LevelBadge from './components/common/LevelBadge.vue'
import StatusPill from './components/common/StatusPill.vue'
import BaseAvatar from './components/common/BaseAvatar.vue'
import KpiCard from './components/common/KpiCard.vue'
import EmptyState from './components/common/EmptyState.vue'

import { installAntiDebug } from './security/antiDebug'
import { useAuthStore } from './stores/auth'
import { useAppStore } from './stores/app'

import './styles/tokens.css'
import './styles/base.css'

const app = createApp(App)

// 全局组件注册（显式命名，避免 <script setup> SFC 缺少 name 导致注册到 undefined）
const globals: Record<string, unknown> = {
  AppIcon,
  VChart,
  SliderCaptcha,
  GlassCard,
  SectionHeader,
  LevelBadge,
  StatusPill,
  BaseAvatar,
  KpiCard,
  EmptyState,
}
for (const [name, comp] of Object.entries(globals)) app.component(name, comp as any)

app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn })

// 应用启动即挂载鉴权状态；主题与反调试仅生产生效
useAuthStore()
useAppStore().initTheme()
useAppStore().initFontScale()
installAntiDebug()

app.mount('#app')
