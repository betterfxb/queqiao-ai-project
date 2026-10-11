<script setup lang="ts">
/**
 * 会员管理 · 右侧高级筛选面板
 * 条件为面板自持状态，点击「应用筛选 / 重置」时向父级抛出事件；
 * 各选项取值与后端 Member 实体字段（height / income / marriage / house / car）保持一致。
 */
import { ref } from 'vue'
import type { AdvancedFilter } from '../types'

const emit = defineEmits<{
  (e: 'apply', value: AdvancedFilter): void
  (e: 'reset'): void
}>()

const H_MIN = 150
const H_MAX = 195

const height = ref<[number, number]>([H_MIN, H_MAX])
const income = ref('不限')
const marriage = ref('不限')
const house = ref('不限')
const car = ref('不限')

/** 与 seed / 后端枚举一致 */
const INCOME_OPTIONS = ['不限', '20-30万', '30-50万', '50-80万', '80-120万', '120万以上']
const MARRIAGE_OPTIONS = ['不限', '未婚', '离异', '丧偶']
const HOUSE_OPTIONS = ['不限', '有房无贷', '有房有贷', '暂无']
const CAR_OPTIONS = ['不限', '有车', '暂无']

const onApply = () =>
  emit('apply', {
    height: height.value,
    income: income.value,
    marriage: marriage.value,
    house: house.value,
    car: car.value,
  })

const onReset = () => {
  height.value = [H_MIN, H_MAX]
  income.value = '不限'
  marriage.value = '不限'
  house.value = '不限'
  car.value = '不限'
  emit('reset')
}
</script>

<template>
  <aside class="adv-panel glass-card">
    <div class="adv-title">高级筛选</div>

    <div class="adv-block">
      <div class="adv-label">身高范围 <span class="adv-val">{{ height[0] }}-{{ height[1] }}cm</span></div>
      <el-slider v-model="height" range :min="H_MIN" :max="H_MAX" />
    </div>

    <div class="adv-block">
      <div class="adv-label">年收入</div>
      <el-select v-model="income" size="small" style="width: 100%">
        <el-option v-for="o in INCOME_OPTIONS" :key="o" :label="o" :value="o" />
      </el-select>
    </div>

    <div class="adv-block">
      <div class="adv-label">婚姻状况</div>
      <div class="mini-chips">
        <span
          v-for="m in MARRIAGE_OPTIONS" :key="m"
          class="chip sm" :class="{ active: marriage === m }"
          @click="marriage = m"
        >{{ m }}</span>
      </div>
    </div>

    <div class="adv-block">
      <div class="adv-label">房产情况</div>
      <el-select v-model="house" size="small" style="width: 100%">
        <el-option v-for="o in HOUSE_OPTIONS" :key="o" :label="o" :value="o" />
      </el-select>
    </div>

    <div class="adv-block">
      <div class="adv-label">购车情况</div>
      <el-select v-model="car" size="small" style="width: 100%">
        <el-option v-for="o in CAR_OPTIONS" :key="o" :label="o" :value="o" />
      </el-select>
    </div>

    <div class="adv-actions">
      <button class="btn btn-ghost" @click="onReset">重置</button>
      <button class="btn btn-primary" @click="onApply">应用筛选</button>
    </div>
  </aside>
</template>

<style scoped>
.adv-panel { width: 280px; flex-shrink: 0; padding: 20px; }
.adv-title { font-size: 0.9375rem; font-weight: 600; color: var(--dt-ink-1); margin-bottom: 16px; }
.adv-block { margin-bottom: 16px; }
.adv-label { font-size: 0.8125rem; color: var(--dt-ink-2); font-weight: 600; display: flex; justify-content: space-between; margin-bottom: 6px; }
.adv-val { color: var(--dt-primary); font-weight: 600; }
.mini-chips { display: flex; gap: 8px; flex-wrap: wrap; }
.chip {
  font-size: 0.75rem; padding: 4px 12px; border-radius: var(--dt-radius-sm); cursor: pointer;
  color: var(--dt-ink-2); background: var(--dt-muted); transition: background 0.15s, color 0.15s; white-space: nowrap;
}
.chip:hover { color: var(--dt-primary); background: var(--dt-primary-soft); }
.chip.active { background: var(--dt-primary); color: #fff; font-weight: 600; }
.chip.sm { padding: 3px 10px; font-size: 0.75rem; }
.adv-actions { display: flex; gap: 12px; margin-top: 8px; }
.adv-actions .btn { flex: 1; }
</style>
