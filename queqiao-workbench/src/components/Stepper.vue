<script setup lang="ts">
/**
 * Stepper — 步进器组件
 * 圆形 +/- 按钮 + 中间可直接输入的数字；v-model:value 双向绑定
 * 支持键盘/手指直接输入年龄，+/- 按钮仍可用，输入失焦时自动 clamp
 */
interface Props {
  /** 当前值（v-model:value） */
  value: number
  /** 步长，默认 1 */
  step?: number
  /** 最小值 */
  min?: number
  /** 最大值 */
  max?: number
  /** 单位后缀，如「岁」 */
  unit?: string
}

const props = withDefaults(defineProps<Props>(), {
  step: 1,
  min: 0,
  max: 999,
  unit: '',
})

const emit = defineEmits<{
  (e: 'update:value', v: number): void
  (e: 'change', v: number): void
}>()

const clamp = (v: number) => Math.min(props.max, Math.max(props.min, v))

const dec = () => {
  const v = clamp(props.value - props.step)
  if (v !== props.value) {
    emit('update:value', v)
    emit('change', v)
  }
}
const inc = () => {
  const v = clamp(props.value + props.step)
  if (v !== props.value) {
    emit('update:value', v)
    emit('change', v)
  }
}

/* ---------- 可编辑数字 ---------- */
const draft = ref(String(props.value))
const focused = ref(false)

// 父组件（或 +/- 按钮）改值时同步草稿，但不打断正在输入的用户
watch(
  () => props.value,
  (v) => {
    if (!focused.value) draft.value = String(v)
  }
)

const onInput = (e: any) => {
  const raw = String(e?.detail?.value ?? e?.target?.value ?? '')
  draft.value = raw.replace(/\D/g, '') // 仅保留数字
}

const commit = () => {
  focused.value = false
  let n = parseInt(draft.value, 10)
  if (isNaN(n)) n = props.value
  n = clamp(n)
  draft.value = String(n)
  if (n !== props.value) {
    emit('update:value', n)
    emit('change', n)
  }
}
</script>

<template>
  <view class="stepper">
    <view
      class="stepper-btn flex-center"
      :class="{ 'is-disabled': value <= min }"
      hover-class="stepper-btn-hover"
      @tap="dec"
    >
      <text class="stepper-sign">−</text>
    </view>

    <view class="stepper-value-wrap flex-center">
      <input
        class="stepper-input"
        type="text"
        inputmode="numeric"
        :value="draft"
        :placeholder="String(min)"
        @input="onInput"
        @focus="focused = true"
        @blur="commit"
        @confirm="commit"
      />
      <text v-if="unit" class="stepper-unit">{{ unit }}</text>
    </view>

    <view
      class="stepper-btn flex-center"
      :class="{ 'is-disabled': value >= max }"
      hover-class="stepper-btn-hover"
      @tap="inc"
    >
      <text class="stepper-sign">＋</text>
    </view>
  </view>
</template>

<style scoped>
.stepper {
  display: flex;
  align-items: center;
  gap: 24rpx;
}
.stepper-btn {
  width: 72rpx;
  height: 72rpx;
  border-radius: 50%;
  background: var(--q-primary-soft);
  border: 2rpx solid rgba(139, 37, 74, 0.16);
  transition: transform 0.15s ease, opacity 0.15s ease;
  flex-shrink: 0;
}
.stepper-btn-hover {
  transform: scale(0.92);
  background: var(--q-primary-soft-2);
}
.stepper-sign {
  font-size: 40rpx;
  font-weight: 600;
  color: var(--q-primary);
  line-height: 1;
}
.is-disabled {
  opacity: 0.35;
}
.stepper-value-wrap {
  min-width: 130rpx;
}
.stepper-input {
  width: 110rpx;
  text-align: center;
  font-size: 52rpx;
  font-weight: 800;
  color: var(--q-ink-1);
  line-height: 1;
  letter-spacing: -0.02em;
  background: transparent;
  /* 去掉数字输入框原生的上下箭头 */
  -webkit-appearance: none;
  appearance: none;
  border: none;
  outline: none;
}
.stepper-unit {
  font-size: 24rpx;
  color: var(--q-ink-3);
  margin-left: 6rpx;
  align-self: flex-end;
  padding-bottom: 6rpx;
}
</style>
