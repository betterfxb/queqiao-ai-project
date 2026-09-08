<script setup lang="ts">
/**
 * LoadingRedLine — 全局加载动画「一线牵」
 * 一条极细的勃艮第红丝线从左向右横扫，替代传统菊花转圈。
 * 用法：<loading-red-line v-if="loading" /> （建议放在页面根部）
 */
interface Props {
  /** 线的垂直位置，默认贴页面顶部 */
  top?: string
}

withDefaults(defineProps<Props>(), {
  top: '0',
})
</script>

<template>
  <view class="red-line-track" :style="{ top }">
    <view class="red-line" />
  </view>
</template>

<style scoped>
.red-line-track {
  position: fixed;
  left: 0;
  right: 0;
  height: 4rpx;
  overflow: hidden;
  z-index: 999;
  background: rgba(139, 37, 74, 0.06);
  pointer-events: none;
}
.red-line {
  width: 40%;
  height: 100%;
  background: linear-gradient(
    90deg,
    transparent 0%,
    var(--q-primary) 35%,
    var(--q-gold) 65%,
    transparent 100%
  );
  border-radius: 4rpx;
  animation: redLineSweep 1.2s ease-in-out infinite;
}
@keyframes redLineSweep {
  0% {
    transform: translateX(-100%);
  }
  100% {
    transform: translateX(350%);
  }
}
</style>
