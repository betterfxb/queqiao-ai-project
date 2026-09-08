<script setup lang="ts">
/**
 * 系统设置 · 通用动态字段渲染
 * 直接消费后端 /api/settings 返回的 SettingItem[]，按 type 渲染控件，
 * 双向绑定到扁平 model（key = setting.id），由父级统一提交 PUT /api/settings。
 */
import type { SettingItem } from '@/api'

defineProps<{
  items: SettingItem[]
  model: Record<string, string>
  columns?: number
}>()

const isSwitch = (v: string) => v === 'true'
</script>

<template>
  <div class="dyn-grid" :class="{ single: columns === 1 }">
    <div v-for="it in items" :key="it.id" class="dyn-item" :class="{ wide: it.type === 'textarea' }">
      <label class="dyn-label" :for="'set-' + it.id">{{ it.label }}</label>

      <el-switch
        v-if="it.type === 'switch'"
        :id="'set-' + it.id"
        :model-value="isSwitch(model[it.id])"
        @update:model-value="(v: any) => (model[it.id] = v ? 'true' : 'false')"
      />

      <el-input
        v-else-if="it.type === 'textarea'"
        :id="'set-' + it.id"
        v-model="model[it.id]"
        type="textarea"
        :rows="3"
        placeholder="每行一个 IP，留空表示不限制"
      />

      <el-input
        v-else-if="it.type === 'number'"
        :id="'set-' + it.id"
        v-model="model[it.id]"
        type="number"
      />

      <el-input v-else :id="'set-' + it.id" v-model="model[it.id]" />

      <span v-if="it.updatedAt" class="dyn-time">更新于 {{ String(it.updatedAt).slice(0, 16).replace('T', ' ') }}</span>
    </div>
  </div>
</template>

<style scoped>
.dyn-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px 28px;
}
.dyn-grid.single { grid-template-columns: 1fr; }
.dyn-item { display: flex; flex-direction: column; gap: 7px; min-width: 0; }
.dyn-item.wide { grid-column: 1 / -1; }
.dyn-label { font-size: 0.8125rem; font-weight: 600; color: var(--dt-ink-2); }
.dyn-time { font-size: 0.7188rem; color: var(--dt-ink-4, var(--dt-ink-3)); }

@media (max-width: 1100px) {
  .dyn-grid { grid-template-columns: 1fr; }
}
</style>
