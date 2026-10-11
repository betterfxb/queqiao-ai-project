<script setup lang="ts">
defineProps<{
  realName?: boolean
  education?: boolean
  asset?: boolean
  realPerson?: boolean
  size?: 'sm' | 'md'
}>()
</script>

<template>
  <span class="verify-badges" :class="size ?? 'md'">
    <span class="vbadge" :class="realName ? 'on' : 'off'">
      <i class="ic">✓</i>实名
    </span>
    <span class="vbadge" :class="education ? 'on' : 'off'">
      <i class="ic">✓</i>学历
    </span>
    <span class="vbadge" :class="asset ? 'on' : 'off'">
      <i class="ic">✓</i>资产
    </span>
    <span class="vbadge rp" :class="realPerson ? 'on' : 'off'">
      <i class="ic">✓</i>实人
    </span>
  </span>
</template>

<style scoped>
.verify-badges { display: inline-flex; gap: 4px; flex-wrap: wrap; }
.vbadge {
  display: inline-flex; align-items: center; gap: 3px;
  padding: 1px 6px; border-radius: var(--dt-radius-sm, 3px); font-size: 0.75rem; font-weight: 500;
  border: 1px solid transparent; line-height: 1.7;
}
.vbadge.sm { font-size: 0.6875rem; padding: 0 5px; }
.vbadge .ic { font-style: normal; font-size: 0.625rem; }
.vbadge.on { color: var(--dt-success, #3f7050); background: var(--dt-success-soft, rgba(63, 112, 80, 0.1)); border-color: rgba(63, 112, 80, 0.24); }
.vbadge.off { color: var(--dt-ink-4, #a8a29e); background: transparent; border-color: var(--dt-border, rgba(28, 25, 23, 0.1)); }
/* 实人认证：品牌主色，区别于绿系认证 */
.vbadge.rp.on { color: var(--dt-primary, #7d2b3d); background: var(--dt-primary-soft-2, rgba(125, 43, 61, 0.1)); border-color: rgba(125, 43, 61, 0.24); }
</style>
