<script setup lang="ts">
/**
 * 系统设置 · 通知偏好 + AI 能力开关（#sec-notify）
 * 全部来自后端 /api/settings 的「通知设置」「AI 设置」分类，父级统一保存。
 */
import type { SettingItem } from '@/api'

const props = defineProps<{
  notifyItems: SettingItem[]
  aiItems: SettingItem[]
  model: Record<string, string>
}>()

const ICONS: Record<string, string> = {
  'notify.newLead': 'user-plus',
  'notify.auditPending': 'shield-check',
  'notify.meetingRemind': 'calendar',
  'notify.dailyReport': 'file-text',
  'ai.enabled': 'sparkles',
  'ai.autoParse': 'scan',
  'ai.autoMatch': 'heart',
  'ai.replySuggest': 'message',
}
const iconOf = (id: string) => ICONS[id] ?? 'bell'
const on = (id: string) => props.model[id] === 'true'
const toggle = (id: string, v: boolean) => (props.model[id] = v ? 'true' : 'false')
</script>

<template>
  <section id="sec-notify" class="glass-card setting-card">
    <section-header title="通知偏好" />
    <div class="toggle-grid">
      <div v-for="it in notifyItems" :key="it.id" class="toggle-item">
        <span class="toggle-label">
          <app-icon :name="iconOf(it.id)" :size="18" />
          {{ it.label }}
        </span>
        <el-switch :model-value="on(it.id)" @update:model-value="(v: any) => toggle(it.id, !!v)" />
      </div>
      <div v-if="!notifyItems.length" class="empty-hint">暂无通知配置项</div>
    </div>

    <section-header title="AI 能力" class="sub-header" />
    <div class="toggle-grid">
      <div v-for="it in aiItems" :key="it.id" class="toggle-item ai">
        <span class="toggle-label">
          <app-icon :name="iconOf(it.id)" :size="18" />
          {{ it.label }}
        </span>
        <el-switch :model-value="on(it.id)" @update:model-value="(v: any) => toggle(it.id, !!v)" />
      </div>
      <div v-if="!aiItems.length" class="empty-hint">暂无 AI 配置项</div>
    </div>
  </section>
</template>

<style scoped>
.setting-card { padding: 22px 24px; }
.setting-card :deep(.section-header) { margin-bottom: 18px; }
.sub-header { margin-top: 22px; }

.toggle-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px 28px;
}
.toggle-item {
  display: flex; align-items: center; justify-content: space-between; gap: 12px;
  padding: 12px 16px;
  border-radius: var(--dt-radius-md);
  background: var(--dt-glass-light);
  border: 1px solid var(--dt-border);
}
.toggle-item.ai { border-color: var(--dt-primary-soft-2); }
.toggle-label {
  display: inline-flex; align-items: center; gap: 10px;
  font-size: 0.875rem; color: var(--dt-ink-2);
}
.empty-hint { grid-column: 1 / -1; color: var(--dt-ink-3); font-size: 0.8125rem; padding: 8px 2px; }

@media (max-width: 1100px) {
  .toggle-grid { grid-template-columns: 1fr; }
}
</style>
