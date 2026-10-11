<script setup lang="ts">
/**
 * 消息中心 · 会话列表（栏1）
 * 纯展示组件：数据与选中态由 index.vue 传入，点击通过 select 事件回传。
 */
import type { Conversation } from '@/api'

defineProps<{
  items: Conversation[]
  activeId: string
}>()

defineEmits<{ (e: 'select', c: Conversation): void }>()
</script>

<template>
  <div class="conv-list">
    <div
      v-for="c in items" :key="c.id"
      class="conv-item" :class="{ active: activeId === c.id }"
      @click="$emit('select', c)"
    >
      <div class="conv-avatar" :class="c.kind">
        <app-icon v-if="c.kind === 'system'" name="bell" :size="18" />
        <template v-else>{{ c.name[0] }}</template>
        <span v-if="c.online" class="online-dot" />
      </div>
      <div class="conv-info">
        <div class="conv-name">{{ c.name }}<span class="conv-time">{{ c.timeAgo }}</span></div>
        <div class="conv-preview">{{ c.preview }}</div>
      </div>
      <span v-if="c.unread" class="unread">{{ c.unread }}</span>
    </div>
  </div>
</template>

<style scoped>
.conv-list { flex: 1; overflow-y: auto; }
.conv-item { display: flex; align-items: center; gap: 10px; padding: 12px 14px; cursor: pointer; border-bottom: 1px solid var(--dt-border); transition: background 0.15s; }
.conv-item:hover { background: var(--dt-primary-soft); }
.conv-item.active { background: var(--dt-primary-soft-2); }
.conv-avatar { position: relative; width: 42px; height: 42px; border-radius: 50%; color: #fff; font-weight: 600; display: flex; align-items: center; justify-content: center; flex-shrink: 0; background: #8d847c; }
.conv-avatar.system { background: var(--dt-primary); font-size: 1rem; }
.online-dot { position: absolute; bottom: 0; right: 0; width: 11px; height: 11px; border-radius: 50%; background: var(--dt-success); border: 2px solid #fff; }
.conv-info { flex: 1; min-width: 0; }
.conv-name { font-weight: 600; color: var(--dt-ink-1); font-size: 0.875rem; display: flex; justify-content: space-between; }
.conv-time { font-size: 0.6875rem; color: var(--dt-ink-4); font-weight: 400; }
.conv-preview { font-size: 0.75rem; color: var(--dt-ink-3); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; margin-top: 2px; }
.unread { min-width: 18px; height: 18px; border-radius: var(--dt-radius-sm); background: var(--dt-error); color: #fff; font-size: 0.6875rem; font-weight: 600; display: flex; align-items: center; justify-content: center; padding: 0 5px; }
</style>
