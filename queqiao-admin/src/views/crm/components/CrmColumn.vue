<script setup lang="ts">
/**
 * CrmColumn — 看板中的一列（阶段标题 + 数量 + 线索卡列表 + 拖拽落点）
 */
import type { CrmCard } from '@/api'
import CrmCardItem from './CrmCard.vue'

defineProps<{
  column: { key: string; title: string; cards: CrmCard[] }
  selectedId?: string
}>()
const emit = defineEmits<{
  (e: 'drop', colKey: string): void
  (e: 'dragstart', id: string): void
  (e: 'select', card: CrmCard): void
}>()
</script>

<template>
  <div
    class="crm-column glass-card"
    @dragover.prevent @drop="emit('drop', column.key)"
  >
    <div class="crm-col-head">
      <span class="crm-col-title">{{ column.title }}</span>
      <span class="crm-col-count">{{ column.cards.length }}</span>
      <app-icon name="plus" :size="14" class="crm-col-add" />
    </div>
    <div class="crm-cards">
      <CrmCardItem
        v-for="card in column.cards" :key="card.id"
        :card="card"
        :selected="selectedId === card.id"
        @dragstart="emit('dragstart', $event)"
        @select="emit('select', $event)"
      />
    </div>
  </div>
</template>

<style scoped>
.crm-column { padding: 12px; min-height: 200px; }
.crm-col-head { display: flex; align-items: center; gap: 8px; margin-bottom: 12px; }
.crm-col-title { font-weight: 700; color: var(--dt-ink-1); font-size: 0.875rem; }
.crm-col-count { background: var(--dt-primary-soft-2); color: var(--dt-primary); font-size: 0.6875rem; font-weight: 700; padding: 0 8px; border-radius: 999px; }
.crm-col-add { margin-left: auto; color: var(--dt-ink-4); cursor: pointer; }
.crm-cards { display: flex; flex-direction: column; gap: 10px; }
</style>
