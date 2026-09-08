<script setup lang="ts">
/** StatusPill — 状态药丸，样式来自全局 .status-pill */
withDefaults(
  defineProps<{
    type?: 'success' | 'warning' | 'error' | 'info' | 'active' | 'gold' | 'neutral'
    text?: string
  }>(),
  { type: 'neutral' },
)
</script>

<template>
  <span class="status-pill" :class="type">
    <span class="dot" />
    <slot>{{ text }}</slot>
  </span>
</template>
