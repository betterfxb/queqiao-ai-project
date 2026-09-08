<script setup lang="ts">
/**
 * AuroraBg — 液态玻璃氛围背景
 * 两组柔性光斑 + 一层动态渐变，用于承载毛玻璃卡片，增强层次感与"温度"。
 * 用法：放在页面根部，其余内容置于其上（默认 z-index 0，内容高于 0）。
 * 可选：dense（更强的光斑强度，适合登录/空态页）
 */
interface Props {
  dense?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  dense: false,
})
</script>

<template>
  <view class="aurora" :class="{ 'aurora-dense': props.dense }">
    <view class="orb orb-a" />
    <view class="orb orb-b" />
    <view class="orb orb-c" />
  </view>
</template>

<style lang="scss" scoped>
.aurora {
  position: fixed;
  inset: 0;
  pointer-events: none;
  z-index: 0;
  overflow: hidden;
}

.orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(60rpx);
  opacity: 0.5;
  will-change: transform;
}

.orb-a {
  width: 480rpx;
  height: 480rpx;
  top: -120rpx;
  right: -100rpx;
  background: radial-gradient(circle, rgba(212, 175, 55, 0.45) 0%, rgba(212, 175, 55, 0) 68%);
  animation: q-glow-drift 11s ease-in-out infinite;
}

.orb-b {
  width: 560rpx;
  height: 560rpx;
  top: 30%;
  left: -220rpx;
  background: radial-gradient(circle, rgba(139, 37, 74, 0.22) 0%, rgba(139, 37, 74, 0) 66%);
  animation: q-glow-drift 14s ease-in-out infinite reverse;
}

.orb-c {
  width: 520rpx;
  height: 520rpx;
  bottom: -160rpx;
  right: -120rpx;
  background: radial-gradient(circle, rgba(201, 122, 150, 0.28) 0%, rgba(201, 122, 150, 0) 66%);
  animation: q-glow-drift 17s ease-in-out infinite;
  animation-delay: -4s;
}

.aurora-dense .orb {
  opacity: 0.7;
}
</style>
