<script setup lang="ts">
/** BaseAvatar — 统一头像（首字母 + 渐变背景，可圆形/方形） */
withDefaults(
  defineProps<{
    name?: string
    bg?: string
    size?: number
    radius?: 'circle' | 'square'
  }>(),
  { size: 38, radius: 'circle' },
)
</script>

<template>
  <div
    class="app-avatar"
    :style="{
      width: size + 'px',
      height: size + 'px',
      background: bg,
      borderRadius: radius === 'square' ? '12px' : '50%',
      fontSize: Math.round(size * 0.4) + 'px',
    }"
  >
    <slot>{{ name?.[0] }}</slot>
  </div>
</template>
