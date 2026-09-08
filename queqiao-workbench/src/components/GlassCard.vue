<script setup lang="ts">
/**
 * GlassCard — 通用毛玻璃卡片容器
 * 设计令牌：rgba(255,255,255,0.6) + blur(20px) + 32px 主圆角
 */
interface Props {
  /** 内边距，默认 32rpx */
  padding?: string
  /** 阴影强度：soft / normal / strong / none */
  shadow?: 'soft' | 'normal' | 'strong' | 'none'
  /** 加强版玻璃（更不透明），默认 false */
  strong?: boolean
  /** 圆角，默认 64rpx（即设计 32px） */
  radius?: string
}

const props = withDefaults(defineProps<Props>(), {
  padding: '32rpx',
  shadow: 'normal',
  strong: false,
  radius: '64rpx',
})

const shadowMap: Record<string, string> = {
  soft: 'var(--q-glass-shadow-soft)',
  normal: 'var(--q-glass-shadow)',
  strong: 'var(--q-glass-shadow-strong)',
  none: 'none',
}
</script>

<template>
  <view
    class="glass-card-wrap"
    :style="{
      padding: props.padding,
      borderRadius: props.radius,
      boxShadow: shadowMap[props.shadow],
      background: props.strong ? 'var(--q-glass-bg-strong)' : 'var(--q-glass-bg)',
    }"
  >
    <slot />
  </view>
</template>

<style scoped>
.glass-card-wrap {
  backdrop-filter: var(--q-glass-blur);
  -webkit-backdrop-filter: var(--q-glass-blur);
  border: 1px solid var(--q-glass-border);
  box-sizing: border-box;
}
</style>
