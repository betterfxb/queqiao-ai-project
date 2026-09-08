<script setup lang="ts">
/** LevelBadge — 会员等级徽标，样式来自全局 .level-badge */
function cls(level: string): string {
  if (level === 'SVIP') return 'svip'
  if (level === 'VIP') return 'vip'
  if (level === '钻石VIP') return 'diamond'
  if (level === '金卡') return 'premium'
  return 'standard'
}
defineProps<{ level: string }>()
</script>

<template>
  <span class="level-badge" :class="cls(level)">{{ level }}</span>
</template>
