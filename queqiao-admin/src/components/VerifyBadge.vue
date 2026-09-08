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
.verify-badges { display: inline-flex; gap: 6px; flex-wrap: wrap; }
.vbadge {
  display: inline-flex; align-items: center; gap: 3px;
  padding: 1px 8px; border-radius: 999px; font-size: 0.75rem; font-weight: 600;
  border: 1px solid transparent; line-height: 1.7;
}
.vbadge.sm { font-size: 0.6875rem; padding: 0 6px; }
.vbadge .ic { font-style: normal; font-size: 0.625rem; }
.vbadge.on { color: #fff; background: linear-gradient(135deg, #2bb673, #1aa86b); }
.vbadge.off { color: var(--dt-ink-3, #99a); background: var(--dt-bg-2, #f1f2f6); border-color: var(--dt-border, #ececf2); }
/* 实人认证：勃艮第酒红渐变，与品牌主色一致，区别于绿系认证 */
.vbadge.rp.on { color: #fff; background: linear-gradient(135deg, #a8456c, #8b254a); }
</style>
