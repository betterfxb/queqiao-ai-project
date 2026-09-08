<script setup lang="ts">
/**
 * GlassCard — 统一玻璃拟态卡片容器
 * 仅做结构包装，视觉样式来自全局 .glass-card（src/styles/base.css）。
 */
withDefaults(
  defineProps<{
    /** 实心背景变体（去掉毛玻璃） */
    flat?: boolean
    /** 内边距，默认 20px；传 '' 可交由外部 scoped 样式控制 */
    padding?: string
  }>(),
  { flat: false, padding: '20px' },
)
</script>

<template>
  <div class="glass-card" :class="{ flat }" :style="padding ? { padding } : undefined">
    <slot />
  </div>
</template>
