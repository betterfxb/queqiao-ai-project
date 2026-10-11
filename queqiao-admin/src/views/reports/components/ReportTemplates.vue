<script setup lang="ts">
/**
 * ReportTemplates — 报表模板区（模板卡网格）
 * 每张卡对应后端 ReportController#download 的一个真实数据分支。
 */
import type { ReportTemplate } from '../types'

defineProps<{ templates: ReportTemplate[]; selected: ReportTemplate; counts?: Record<string, number> }>()
const emit = defineEmits<{ (e: 'pick', t: ReportTemplate): void; (e: 'export', t: ReportTemplate): void }>()
</script>

<template>
  <div class="rep-templates">
    <div class="tpl-grid">
      <div
        v-for="t in templates"
        :key="t.cat"
        class="glass-card tpl-card"
        :class="{ selected: selected.cat === t.cat }"
        @click="emit('pick', t)"
      >
        <div class="tpl-head">
          <app-icon :name="t.icon" :size="20" class="text-primary" />
          <span v-if="counts && counts[t.cat] !== undefined" class="rows">{{ counts[t.cat] }} 条</span>
        </div>
        <div class="tpl-name">{{ t.name }}</div>
        <div class="tpl-desc">{{ t.desc }}</div>
        <div class="tpl-formats">
          <span v-for="f in t.formats" :key="f" class="fmt">{{ f }}</span>
        </div>
        <button class="btn btn-sm btn-outline tpl-btn" @click.stop="emit('export', t)">
          <app-icon name="download" :size="14" /> 生成并下载
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.rep-templates { flex: 1; min-width: 0; }
.tpl-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 14px; }
.tpl-card { padding: 16px; cursor: pointer; position: relative; transition: border-color 0.15s, background 0.15s; border: 1px solid var(--dt-border); display: flex; flex-direction: column; }
.tpl-card:hover { border-color: var(--dt-border-strong); background: var(--dt-bg-2); }
.tpl-card.selected { border-color: var(--dt-gold); }
.tpl-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
.rows { font-size: 0.6875rem; color: var(--dt-gold-dark); font-weight: 600; }
.tpl-name { font-size: 0.875rem; font-weight: 600; color: var(--dt-ink-1); margin-bottom: 6px; }
.tpl-desc { font-size: 0.75rem; color: var(--dt-ink-3); line-height: 1.5; margin-bottom: 10px; flex: 1; }
.tpl-formats { display: flex; gap: 6px; margin-bottom: 12px; }
.fmt { font-size: 0.6875rem; padding: 2px 8px; border-radius: var(--dt-radius-sm); background: var(--dt-muted); color: var(--dt-ink-3); }
.tpl-btn { width: 100%; }

@media (max-width: 1400px) {
  .tpl-grid { grid-template-columns: repeat(2, 1fr); }
}
</style>
