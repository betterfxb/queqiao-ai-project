<script setup lang="ts">
/**
 * AppIcon — 跨端图标组件
 * 小程序/App 端不支持内联 SVG，统一使用离线渲染的 PNG 图标（src/static/icons）。
 * 新增图标：在 tools/icon-pipeline/gen_icons.py 的 SPECS 中登记后重新生成。
 */
interface Props {
  /** 图标名（不含 .png 后缀），如 'bell'、'mic' */
  name: string
  /** 尺寸，默认 40rpx */
  size?: string
}

withDefaults(defineProps<Props>(), {
  size: '40rpx',
})
</script>

<template>
  <image
    :src="`/static/icons/${name}.png`"
    :style="{ width: size, height: size }"
    mode="aspectFit"
    class="app-icon"
  />
</template>

<style scoped>
.app-icon {
  display: block;
  flex-shrink: 0;
}
</style>
