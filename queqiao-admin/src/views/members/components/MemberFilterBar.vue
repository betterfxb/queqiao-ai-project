<script setup lang="ts">
/**
 * 会员管理 · 顶部搜索操作条 + 筛选 chips 条
 * 筛选状态由父级持有（表格需要用它做过滤），此处通过 v-model / props 双向同步。
 */
import type { ChipGroup, MemberView } from '../types'

defineProps<{ groups: ChipGroup[]; exporting?: boolean }>()
const emit = defineEmits<{
  (e: 'new-member'): void
  (e: 'export'): void
  (e: 'refresh'): void
}>()

const search = defineModel<string>('search', { required: true })
const view = defineModel<MemberView>('view', { required: true })
const sort = defineModel<string>('sort', { required: true })
</script>

<template>
  <!-- 顶部操作 -->
  <div class="top-actions">
    <div class="search-box">
      <app-icon name="search" :size="16" />
      <input v-model="search" type="text" placeholder="搜索会员姓名、ID、手机号..." />
    </div>
    <div style="flex:1" />
    <button class="btn btn-outline" @click="emit('refresh')"><app-icon name="refresh" :size="15" />刷新</button>
    <button class="btn btn-outline" :disabled="exporting" @click="emit('export')">
      <app-icon name="download" :size="15" />{{ exporting ? '导出中…' : '导出 CSV' }}
    </button>
    <button class="btn btn-gold" @click="emit('new-member')"><app-icon name="plus" :size="15" />新增会员</button>
  </div>

  <!-- 筛选 chips -->
  <div class="chips-bar glass-card">
    <div v-for="g in groups" :key="g.key" class="chip-group">
      <span class="chip-label">{{ g.label }}</span>
      <span
        v-for="opt in g.options" :key="opt"
        class="chip" :class="{ active: g.active === opt }"
        @click="g.active = opt"
      >{{ opt }}</span>
    </div>
    <div class="chips-right">
      <div class="view-toggle">
        <app-icon name="list" :size="16" :class="{ on: view === 'list' }" @click="view = 'list'" />
        <app-icon name="grid" :size="16" :class="{ on: view === 'grid' }" @click="view = 'grid'" />
      </div>
      <el-select v-model="sort" size="small" style="width: 130px">
        <el-option label="最近活跃" value="最近活跃" />
        <el-option label="最新注册" value="最新注册" />
        <el-option label="牵线次数" value="牵线次数" />
      </el-select>
    </div>
  </div>
</template>

<style scoped>
.top-actions { display: flex; align-items: center; gap: 12px; }
.search-box {
  display: flex; align-items: center; gap: 8px; width: 360px; height: 38px; padding: 0 12px;
  border-radius: 999px; background: var(--dt-glass-light); border: 1px solid var(--dt-border); color: var(--dt-ink-3);
}
.search-box input { flex: 1; border: none; outline: none; background: transparent; font-size: 0.8125rem; font-family: var(--dt-font); color: var(--dt-ink-1); }
.search-box input::placeholder { color: var(--dt-ink-4); }

.chips-bar { display: flex; align-items: center; flex-wrap: wrap; gap: 18px; padding: 12px 18px; }
.chip-group { display: flex; align-items: center; gap: 8px; }
.chip-label { font-size: 0.8125rem; color: var(--dt-ink-3); font-weight: 600; margin-right: 2px; }
.chip {
  font-size: 0.75rem; padding: 4px 12px; border-radius: 999px; cursor: pointer;
  color: var(--dt-ink-2); background: var(--dt-muted); transition: all 0.15s; white-space: nowrap;
}
.chip:hover { color: var(--dt-primary); }
.chip.active { background: var(--dt-primary); color: #fff; font-weight: 600; }
.chips-right { margin-left: auto; display: flex; align-items: center; gap: 12px; }
.view-toggle { display: flex; gap: 6px; color: var(--dt-ink-4); }
.view-toggle .on { color: var(--dt-primary); }
.view-toggle svg { cursor: pointer; }
</style>
