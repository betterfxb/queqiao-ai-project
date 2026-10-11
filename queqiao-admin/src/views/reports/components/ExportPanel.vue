<script setup lang="ts">
/**
 * ExportPanel — 导出配置（时间范围 + 格式 + 操作）
 * 所有选项都会真实提交到 POST /api/reports/generate，
 * 后端按 category 现算真实数据并生成可下载的 CSV。
 */
import { computed, ref, watch } from 'vue'
import type { ReportTemplate } from '../types'

const props = defineProps<{ selected: ReportTemplate; busy?: boolean }>()
const emit = defineEmits<{
  (e: 'preview'): void
  (e: 'export', payload: { category: string; format: string; range: string }): void
}>()

const today = new Date()
const iso = (d: Date) => d.toISOString().slice(0, 10)
const rangeStart = ref(iso(new Date(today.getFullYear(), today.getMonth(), 1)))
const rangeEnd = ref(iso(today))
const rangeChip = ref('本月')
const format = ref('CSV')

watch(
  () => props.selected,
  () => {
    if (!props.selected.formats.includes(format.value)) format.value = props.selected.formats[0]
  },
)

const applyChip = (c: string) => {
  rangeChip.value = c
  const y = today.getFullYear()
  const m = today.getMonth()
  const map: Record<string, [Date, Date]> = {
    今日: [today, today],
    本周: [new Date(today.getTime() - ((today.getDay() + 6) % 7) * 864e5), today],
    本月: [new Date(y, m, 1), today],
    本季度: [new Date(y, Math.floor(m / 3) * 3, 1), today],
    本年: [new Date(y, 0, 1), today],
  }
  const r = map[c]
  if (r) {
    rangeStart.value = iso(r[0])
    rangeEnd.value = iso(r[1])
  }
}

const rangeText = computed(() => `${rangeStart.value} ~ ${rangeEnd.value}`)

const doExport = () =>
  emit('export', { category: props.selected.cat, format: format.value, range: rangeText.value })
</script>

<template>
  <aside class="glass-card rep-config">
    <div class="cfg-selected">
      <app-icon :name="selected.icon" :size="18" class="text-gold" />
      <div>
        <div class="cfg-sel-name">{{ selected.name }}</div>
        <div class="cfg-sel-fmt">分类 {{ selected.cat }} · {{ format }}</div>
      </div>
    </div>

    <div class="cfg-block">
      <div class="cfg-label">时间范围</div>
      <div class="cfg-dates">
        <el-date-picker v-model="rangeStart" type="date" value-format="YYYY-MM-DD" size="small" style="flex: 1" />
        <span class="text-muted">至</span>
        <el-date-picker v-model="rangeEnd" type="date" value-format="YYYY-MM-DD" size="small" style="flex: 1" />
      </div>
      <div class="cfg-chips">
        <span
          v-for="c in ['今日', '本周', '本月', '本季度', '本年']"
          :key="c"
          class="chip sm"
          :class="{ active: rangeChip === c }"
          @click="applyChip(c)"
        >{{ c }}</span>
      </div>
    </div>

    <div class="cfg-block">
      <div class="cfg-label">导出格式</div>
      <div class="fmt-cards">
        <div
          v-for="f in selected.formats"
          :key="f"
          class="fmt-card"
          :class="{ active: format === f }"
          @click="format = f"
        >
          <app-icon name="file-text" :size="18" />
          <span>{{ f }}</span>
          <app-icon v-if="format === f" name="check-circle" :size="14" class="fmt-check" />
        </div>
      </div>
      <div class="cfg-hint">导出为 UTF-8 BOM 编码，Excel / WPS 可直接打开。</div>
    </div>

    <div class="cfg-block">
      <div class="cfg-label">数据说明</div>
      <p class="cfg-desc">{{ selected.desc }}</p>
    </div>

    <div class="cfg-actions">
      <button class="btn btn-outline" style="flex: 1" :disabled="busy" @click="$emit('preview')">
        预览数据
      </button>
      <button class="btn btn-gold" style="flex: 1" :disabled="busy" @click="doExport">
        <app-icon name="download" :size="15" />{{ busy ? '生成中…' : '立即导出' }}
      </button>
    </div>
  </aside>
</template>

<style scoped>
.rep-config { width: 340px; flex-shrink: 0; padding: 20px; }
.cfg-selected { display: flex; gap: 10px; align-items: center; background: var(--dt-gold-soft); border-radius: var(--dt-radius-md); padding: 12px; margin-bottom: 16px; }
.cfg-sel-name { font-weight: 600; color: var(--dt-ink-1); }
.cfg-sel-fmt { font-size: 0.75rem; color: var(--dt-ink-3); }
.cfg-block { margin-bottom: 16px; }
.cfg-label { font-size: 0.8125rem; font-weight: 600; color: var(--dt-ink-1); margin-bottom: 8px; }
.cfg-dates { display: flex; align-items: center; gap: 8px; }
.cfg-chips { display: flex; gap: 6px; flex-wrap: wrap; margin-top: 10px; }
.chip { font-size: 0.75rem; padding: 4px 14px; border-radius: var(--dt-radius-sm); cursor: pointer; color: var(--dt-ink-2); background: var(--dt-muted); }
.chip.active { background: var(--dt-primary); color: #fff; font-weight: 600; }
.chip.sm { padding: 3px 10px; }
.fmt-cards { display: flex; gap: 10px; }
.fmt-card { flex: 1; position: relative; display: flex; flex-direction: column; align-items: center; gap: 6px; padding: 12px; border-radius: var(--dt-radius-md); border: 1px solid var(--dt-border); cursor: pointer; color: var(--dt-ink-2); transition: border-color 0.15s, background 0.15s, color 0.15s; font-size: 0.8125rem; font-weight: 600; }
.fmt-card.active { border-color: var(--dt-gold); background: var(--dt-gold-soft); color: var(--dt-gold-dark); }
.fmt-check { position: absolute; top: 6px; right: 6px; color: var(--dt-gold-dark); }
.cfg-hint { font-size: 0.7188rem; color: var(--dt-ink-4); margin-top: 8px; line-height: 1.5; }
.cfg-desc { font-size: 0.7813rem; color: var(--dt-ink-3); line-height: 1.6; margin: 0; }
.cfg-actions { display: flex; gap: 10px; }
</style>
