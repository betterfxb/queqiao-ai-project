<script setup lang="ts">
/**
 * 系统设置 · 外观主题（#sec-theme）+ 全局字体大小（#sec-font）
 * 选中态取自全局 app store，切换即写入 <html data-theme> 并持久化。
 * 字体大小以滑块（进度条）形式调节：拖动实时改根字号变量、流畅预览，松手持久化到本地与后端。
 */
import { ref } from 'vue'
import { THEMES } from '@/constants/theme'
import { useAppStore } from '@/stores/app'

const app = useAppStore()

const FONT_MIN = 0.8
const FONT_MAX = 1.4
const FONT_STEP = 0.05

const fontScale = ref(app.fontScale)
// 标准正文基准 14px，按滑块系数换算当前有效字号（仅缩放文字，不改布局）
const basePx = (v: number) => Math.round(14 * v) + 'px'

// 拖动中：仅实时改根字号变量（CSS 变量 + rAF 节流），流畅无卡顿
const onInput = (v: number) => app.setFontScale(v)
// 松手：再同步持久化到本地与后端（ui.fontScale），跨设备一致
const onCommit = (v: number) => app.setFontScale(v, true)
const resetFont = () => {
  fontScale.value = 1
  app.setFontScale(1, true)
}
</script>

<template>
  <section id="sec-theme" class="glass-card setting-card">
    <section-header title="外观主题" />
    <div class="theme-grid">
      <div
        v-for="t in THEMES"
        :key="t.key"
        class="theme-card"
        :class="{ selected: app.theme === t.key }"
        @click="app.setTheme(t.key)"
      >
        <div class="theme-check"><app-icon name="check" :size="14" /></div>
        <div class="theme-swatch" :class="'sw-' + t.key">
          <span class="swatch-dot sw-primary" />
          <span class="swatch-dot sw-accent" />
          <span class="swatch-dot sw-bg" />
        </div>
        <div class="theme-name">{{ t.name }}</div>
      </div>
    </div>
  </section>

  <section id="sec-font" class="glass-card setting-card">
    <section-header title="全局字体大小">
      <span class="font-pct">{{ basePx(fontScale) }}</span>
    </section-header>
    <p class="tab-hint">
      拖动滑块按需放大或缩小文字大小，仅调整字号、不改变页面布局；选择会即时预览并自动记住，刷新或换设备登录后依旧生效。
    </p>
    <div class="font-row">
      <span class="font-end">小</span>
      <el-slider
        v-model="fontScale"
        class="font-slider"
        :min="FONT_MIN"
        :max="FONT_MAX"
        :step="FONT_STEP"
        :format-tooltip="basePx"
        @input="onInput"
        @change="onCommit"
      />
      <span class="font-end">大</span>
      <button class="btn btn-ghost btn-sm" :disabled="fontScale === 1" @click="resetFont">
        <app-icon name="rotate-ccw" :size="14" /> 默认
      </button>
    </div>
    <div class="font-preview" :style="{ fontSize: basePx(fontScale) }">牵线运营中心 · 预览文字大小</div>
  </section>
</template>

<style scoped>
.setting-card { padding: 22px 24px; }
.setting-card .section-header { margin-bottom: 18px; }

.theme-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; }
.theme-card {
  position: relative;
  padding: 18px;
  border-radius: var(--dt-radius-lg);
  border: 1px solid var(--dt-border);
  background: var(--dt-card-solid);
  cursor: pointer;
  transition: border-color 0.15s ease, background 0.15s ease;
}
.theme-card:hover { border-color: var(--dt-border-strong); background: var(--dt-muted); }
.theme-card.selected { border-color: var(--dt-primary); background: var(--dt-primary-soft); box-shadow: var(--dt-shadow-sm); }
.theme-check {
  position: absolute; top: 12px; right: 12px;
  width: 22px; height: 22px; border-radius: 50%;
  background: var(--dt-primary); color: #fff;
  display: flex; align-items: center; justify-content: center;
  opacity: 0; transform: scale(0.6); transition: all 0.18s ease;
}
.theme-card.selected .theme-check { opacity: 1; transform: scale(1); }
.theme-swatch { display: flex; gap: 8px; margin-bottom: 14px; }
.swatch-dot {
  width: 36px; height: 36px; border-radius: var(--dt-radius-md);
  border: 1px solid var(--dt-border);
}
/* 预览色块展示各主题的真实主色 / 点缀色 / 纸面色（实色，无渐变） */
.sw-burgundy .sw-primary { background: #7d2b3d; }
.sw-burgundy .sw-accent { background: #a8863f; }
.sw-burgundy .sw-bg { background: #f6f5f3; }
.sw-mint .sw-primary { background: #3f7261; }
.sw-mint .sw-accent { background: #a8863f; }
.sw-mint .sw-bg { background: #f4f6f4; }
.sw-navy .sw-primary { background: #2f4160; }
.sw-navy .sw-accent { background: #a8863f; }
.sw-navy .sw-bg { background: #f4f5f7; }
.theme-name { font-size: 0.875rem; font-weight: 600; color: var(--dt-ink-1); }

/* —— 全局字体大小 —— */
.font-pct {
  margin-left: auto;
  font-size: 0.875rem;
  font-weight: 600;
  color: var(--dt-primary);
  font-family: var(--dt-font-num);
}
.font-row {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-top: 4px;
}
.font-end {
  font-size: 0.75rem;
  color: var(--dt-ink-3);
  flex-shrink: 0;
  user-select: none;
}
.font-slider { flex: 1; }
.font-slider :deep(.el-slider__runway) {
  background: var(--dt-muted);
}
.font-slider :deep(.el-slider__bar) {
  background: var(--dt-primary);
}
.font-slider :deep(.el-slider__button) {
  border-color: var(--dt-primary);
  background: #fff;
}
.font-preview {
  margin-top: 16px;
  padding: 12px 16px;
  border-radius: var(--dt-radius-md);
  background: var(--dt-card-solid);
  border: 1px solid var(--dt-border);
  color: var(--dt-ink-1);
  font-weight: 600;
  line-height: 1.4;
}
</style>
