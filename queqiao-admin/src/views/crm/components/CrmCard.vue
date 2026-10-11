<script setup lang="ts">
/**
 * CrmCard — 看板中的单张线索卡（头像 / 姓名年龄 / 等级手机 / 新鲜度 / 下一步动作）
 */
import type { CrmCard } from '@/api'

withDefaults(defineProps<{ card: CrmCard; selected?: boolean }>(), { selected: false })
const emit = defineEmits<{
  (e: 'dragstart', id: string): void
  (e: 'select', card: CrmCard): void
}>()

/** 性别 → 头像底色（实色，与全站头像体系一致） */
const avatarBg = (g: string) => (g === '女' ? '#a8607a' : '#4f6d8f')
const freshColor = (f: string) => (f === 'fresh' ? 'var(--dt-success)' : f === 'warning' ? 'var(--dt-warning)' : 'var(--dt-error)')
</script>

<template>
  <div
    class="crm-card" :class="{ selected }"
    draggable="true" @dragstart="emit('dragstart', card.id)" @click="emit('select', card)"
  >
    <div class="crm-card-top">
      <BaseAvatar class="crm-avatar" :size="34" :bg="avatarBg(card.gender)">{{ card.name[0] }}</BaseAvatar>
      <div class="crm-card-info">
        <div class="crm-card-name">
          {{ card.name }}
          <app-icon v-if="card.star" name="star" :size="12" style="color:var(--dt-gold)" />
        </div>
        <div class="crm-card-sub">{{ card.age ? card.age + '岁' : '' }}</div>
      </div>
      <app-icon name="grip" :size="14" class="crm-grip" />
    </div>
    <div class="crm-card-meta">
      <LevelBadge :level="card.level" />
      <span v-if="card.phone" class="crm-phone">{{ card.phone }}</span>
    </div>
    <div class="crm-card-fresh">
      <span class="fresh-dot" :style="{ background: freshColor(card.fresh) }" />{{ card.freshText }}
      <span v-if="card.stayDays" class="stay">跟进{{ card.stayDays }}天</span>
    </div>
    <div class="crm-next"><app-icon name="clock" :size="11" />{{ card.nextAction }}</div>
  </div>
</template>

<style scoped>
.crm-card { background: var(--dt-card-solid); border: 1px solid var(--dt-border); border-radius: var(--dt-radius-md); padding: 12px; cursor: grab; transition: border-color 0.15s; }
.crm-card:hover { border-color: var(--dt-border-strong); }
.crm-card.selected { border-color: var(--dt-primary); }
.crm-card-top { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
/* 尺寸/背景由 BaseAvatar 内联样式给出，这里仅补字重 */
.crm-avatar { font-weight: 600; }
.crm-card-info { flex: 1; min-width: 0; }
.crm-card-name { font-weight: 600; color: var(--dt-ink-1); font-size: 0.875rem; display: flex; align-items: center; gap: 4px; }
.crm-card-sub { font-size: 0.6875rem; color: var(--dt-ink-4); }
.crm-grip { color: var(--dt-ink-4); }
.crm-card-meta { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.crm-phone { font-size: 0.6875rem; color: var(--dt-ink-3); }
.crm-card-fresh { display: flex; align-items: center; gap: 5px; font-size: 0.6875rem; color: var(--dt-ink-3); margin-bottom: 8px; }
.fresh-dot { width: 8px; height: 8px; border-radius: 50%; }
.stay { margin-left: auto; color: var(--dt-ink-4); }
.crm-next { font-size: 0.6875rem; color: var(--dt-primary); background: var(--dt-primary-soft); border-radius: var(--dt-radius-sm); padding: 4px 8px; display: flex; align-items: center; gap: 4px; }
</style>
