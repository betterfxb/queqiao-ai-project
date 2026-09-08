<script setup lang="ts">
/**
 * SectionHeader — 区块标题（金色竖条 + 标题 + 右侧操作区）
 * - 默认右侧渲染「查看全部 / 更多」文字按钮（more 事件）
 * - 也可用 #actions 插槽自定义右侧内容
 */
defineProps<{ title: string; moreText?: string }>()
defineEmits<{ (e: 'more'): void }>()
</script>

<template>
  <div class="section-header">
    <div class="section-title">
      <span class="section-title-bar" />
      {{ title }}
    </div>
    <slot name="actions">
      <div v-if="moreText" class="section-more" @click="$emit('more')">
        {{ moreText }}
        <app-icon name="chevron-right" :size="14" />
      </div>
    </slot>
  </div>
</template>
