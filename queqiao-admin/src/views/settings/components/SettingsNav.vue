<script setup lang="ts">
/**
 * 系统设置 · 左侧子导航（220px 吸顶）
 * 仅负责展示与派发 select；滚动定位逻辑在 index.vue。
 */
import { SETTINGS_NAV } from '../constants'

defineProps<{ activeKey: string }>()
defineEmits<{ (e: 'select', key: string): void }>()
</script>

<template>
  <aside class="settings-nav glass-card">
    <div v-for="g in SETTINGS_NAV" :key="g.title" class="nav-section">
      <div class="nav-section-header">{{ g.title }}</div>
      <a
        v-for="it in g.items"
        :key="it.key"
        class="settings-nav-item"
        :class="{ active: activeKey === it.key }"
        @click="$emit('select', it.key)"
      >
        <app-icon :name="it.icon" :size="16" />
        <span>{{ it.label }}</span>
      </a>
    </div>
  </aside>
</template>

<style scoped>
.settings-nav {
  width: 220px;
  flex-shrink: 0;
  padding: 12px;
  position: sticky;
  top: 0;
  max-height: calc(100vh - 64px - 64px);
  overflow-y: auto;
}
.nav-section { margin-bottom: 14px; }
.nav-section:last-child { margin-bottom: 0; }
.nav-section-header {
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--dt-ink-4);
  padding: 6px 10px;
}
.settings-nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 12px;
  border-radius: var(--dt-radius-md);
  font-size: 0.8438rem;
  color: var(--dt-ink-2);
  cursor: pointer;
  transition: all 0.16s ease;
}
.settings-nav-item:hover { background: var(--dt-muted); color: var(--dt-primary); }
.settings-nav-item.active {
  background: var(--dt-primary-soft-2);
  color: var(--dt-primary);
  font-weight: 600;
}

@media (max-width: 1100px) {
  .settings-nav { width: 100%; position: static; max-height: none; }
}
</style>
