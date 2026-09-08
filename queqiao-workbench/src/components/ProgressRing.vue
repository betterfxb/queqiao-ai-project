<script setup lang="ts">
/**
 * ProgressRing — 匹配度环形进度条（香槟金描边）
 * 说明：小程序端不支持内联 SVG，故采用 conic-gradient 环形方案，
 * 三端渲染一致；中心显示百分比数值。
 */
import { computed } from 'vue'

interface Props {
  /** 百分比 0-100 */
  percent: number
  /** 直径，默认 96rpx */
  size?: string
  /** 环宽，默认 8rpx */
  stroke?: string
}

const props = withDefaults(defineProps<Props>(), {
  size: '96rpx',
  stroke: '8rpx',
})

const deg = computed(() => Math.min(100, Math.max(0, props.percent)) * 3.6)
</script>

<template>
  <view
    class="ring"
    :style="{
      width: props.size,
      height: props.size,
      background: `conic-gradient(#D4AF37 0deg, #E3C766 ${deg}deg, rgba(139,37,74,0.08) ${deg}deg 360deg)`,
    }"
  >
    <view class="ring-hole flex-center" :style="{ margin: props.stroke }">
      <text class="ring-text">{{ percent }}<text class="ring-pct">%</text></text>
    </view>
  </view>
</template>

<style scoped>
.ring {
  border-radius: 50%;
  box-shadow: 0 0 16rpx rgba(212, 175, 55, 0.25);
}
.ring-hole {
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.92);
  height: 100%;
  box-sizing: border-box;
}
.ring-text {
  font-size: 28rpx;
  font-weight: 800;
  color: var(--q-gold-dark);
  line-height: 1;
}
.ring-pct {
  font-size: 18rpx;
  font-weight: 600;
}
</style>
