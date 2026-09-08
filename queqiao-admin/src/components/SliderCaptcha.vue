<script setup lang="ts">
/**
 * 滑块拼图验证码（纯前端自包含，无外部依赖 / 无第三方打码平台）
 * 用途：登录前的人机校验（反自动化脚本 / 反爬虫爆破）
 * 实现：在轨道随机位置生成“目标缺口”，用户拖动滑块落入缺口即校验通过。
 * 说明：这是前端 deterrent；强校验仍需服务端下发 ticket（见登录流程注释）。
 */
import { ref, onMounted, onBeforeUnmount } from 'vue'

const emit = defineEmits<{ (e: 'success'): void; (e: 'reset'): void }>()

const TRACK_W = 320
const TRACK_H = 44
const KNOB = 44
const TOL = 8

const targetX = ref(0) // 缺口中心 x
const knobX = ref(0) // 滑块当前 x
const dragging = ref(false)
const verified = ref(false)
const failed = ref(false)

let startX = 0
let startKnob = 0

function rand(min: number, max: number) {
  return Math.floor(Math.random() * (max - min) + min)
}

function reset() {
  verified.value = false
  failed.value = false
  knobX.value = 0
  targetX.value = rand(TRACK_W * 0.25, TRACK_W * 0.75)
  emit('reset')
}

function onDown(e: PointerEvent) {
  if (verified.value) return
  dragging.value = true
  startX = e.clientX
  startKnob = knobX.value
  ;(e.target as HTMLElement).setPointerCapture?.(e.pointerId)
}
function onMove(e: PointerEvent) {
  if (!dragging.value) return
  const dx = e.clientX - startX
  let nx = startKnob + dx
  nx = Math.max(0, Math.min(TRACK_W - KNOB, nx))
  knobX.value = nx
}
function onUp() {
  if (!dragging.value) return
  dragging.value = false
  const center = knobX.value + KNOB / 2
  if (Math.abs(center - targetX.value) <= TOL) {
    verified.value = true
    failed.value = false
    knobX.value = targetX.value - KNOB / 2
    emit('success')
  } else {
    failed.value = true
    setTimeout(() => {
      if (!verified.value) reset()
    }, 500)
  }
}

let onKey: (e: KeyboardEvent) => void
onMounted(() => {
  reset()
  onKey = (e: KeyboardEvent) => {
    if (verified.value) return
    if (e.key === 'ArrowRight') knobX.value = Math.min(TRACK_W - KNOB, knobX.value + 6)
    if (e.key === 'ArrowLeft') knobX.value = Math.max(0, knobX.value - 6)
  }
  window.addEventListener('keydown', onKey)
})
onBeforeUnmount(() => window.removeEventListener('keydown', onKey))

defineExpose({ reset, verified })
</script>

<template>
  <div class="slider" :class="{ verified, failed }">
    <div
      class="track"
      :style="{ width: TRACK_W + 'px', height: TRACK_H + 'px' }"
      @pointermove="onMove"
      @pointerup="onUp"
      @pointerleave="onUp"
    >
      <!-- 目标缺口 -->
      <div
        class="target"
        :style="{ left: targetX - KNOB / 2 + 'px', width: KNOB + 'px' }"
      />
      <!-- 已滑过进度 -->
      <div class="fill" :style="{ width: knobX + KNOB / 2 + 'px' }" />
      <!-- 滑块旋钮 -->
      <div
        class="knob"
        :style="{ left: knobX + 'px', width: KNOB + 'px', height: KNOB + 'px' }"
        @pointerdown="onDown"
      >
        <app-icon v-if="!verified" name="arrow-left" :size="20" style="transform: rotate(180deg)" />
        <app-icon v-else name="check" :size="22" />
      </div>
      <!-- 提示文字 -->
      <span v-if="!verified && !failed" class="hint">拖动滑块完成拼图验证</span>
      <span v-else-if="failed" class="hint err">验证失败，请重试</span>
      <span v-else class="hint ok">验证通过</span>
    </div>
  </div>
</template>

<style scoped>
.slider {
  user-select: none;
  -webkit-user-select: none;
  touch-action: none;
}
.track {
  position: relative;
  border-radius: 999px;
  background: var(--dt-input, #f1ece4);
  border: 1px solid var(--dt-input-border, #e4dccc);
  overflow: hidden;
}
.target {
  position: absolute;
  top: 0;
  bottom: 0;
  border-radius: 999px;
  background: rgba(122, 33, 53, 0.12);
  border: 2px dashed rgba(122, 33, 53, 0.4);
  box-sizing: border-box;
}
.fill {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  background: linear-gradient(90deg, rgba(201, 169, 110, 0.35), rgba(201, 169, 110, 0.55));
}
.knob {
  position: absolute;
  top: 0;
  border-radius: 999px;
  background: var(--dt-gold-gradient, linear-gradient(135deg, #c9a96e, #a8824a));
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: grab;
  box-shadow: 0 6rpx 16rpx -6rpx rgba(122, 33, 53, 0.4);
  z-index: 2;
}
.knob:active {
  cursor: grabbing;
}
.hint {
  position: absolute;
  left: 0;
  right: 0;
  top: 50%;
  transform: translateY(-50%);
  text-align: center;
  font-size: 0.8125rem;
  color: var(--dt-ink-3, #7a6670);
  pointer-events: none;
  z-index: 1;
}
.hint.err {
  color: #b53a4a;
}
.hint.ok {
  color: #4a8b5c;
}
.slider.verified .fill {
  background: linear-gradient(90deg, rgba(74, 139, 92, 0.35), rgba(74, 139, 92, 0.55));
}
</style>
