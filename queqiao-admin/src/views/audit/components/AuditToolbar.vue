<script setup lang="ts">
/**
 * AuditToolbar — 审核工作台 Tabs + 筛选条（时间/红娘/紧急程度/搜索）
 * 所有筛选项通过 v-model 双向绑定回父级。
 */
import { ref } from 'vue'

const activeTab = defineModel<string>('activeTab', { required: true })
const timeRange = defineModel<string>('timeRange', { required: true })
const agentFilter = defineModel<string>('agentFilter', { required: true })
const urgencyFilter = defineModel<string>('urgencyFilter', { required: true })
const search = defineModel<string>('search', { required: true })

const tabs = ref([
  { key: 'all', label: '全部', count: 45 },
  { key: 'cert', label: '实名认证', count: 5 },
  { key: 'edit', label: '资料修改', count: 3 },
  { key: 'upgrade', label: '会员升级', count: 2 },
  { key: 'activity', label: '活动报名', count: 2 },
])
</script>

<template>
  <div class="filter-bar glass-card">
    <div class="audit-tabs">
      <span
        v-for="t in tabs" :key="t.key"
        class="audit-tab" :class="{ active: activeTab === t.key }"
        @click="activeTab = t.key"
      >{{ t.label }}<span class="tab-count">{{ t.count }}</span></span>
    </div>
    <div class="filter-right">
      <el-select v-model="timeRange" size="small" style="width:100px"><el-option label="今日" value="今日" /><el-option label="本周" value="本周" /></el-select>
      <el-select v-model="agentFilter" size="small" style="width:100px"><el-option label="全部红娘" value="全部" /><el-option label="王姐" value="王姐" /></el-select>
      <el-select v-model="urgencyFilter" size="small" style="width:100px"><el-option label="全部" value="全部" /><el-option label="紧急" value="紧急" /></el-select>
      <div class="search-mini"><app-icon name="search" :size="14" /><input v-model="search" placeholder="搜索申请人..." /></div>
    </div>
  </div>
</template>

<style scoped>
.filter-bar { display: flex; align-items: center; justify-content: space-between; padding: 12px 18px; flex-wrap: wrap; gap: 12px; }
.audit-tabs { display: flex; gap: 4px; }
.audit-tab { display: inline-flex; align-items: center; gap: 6px; padding: 6px 14px; border-radius: var(--dt-radius-sm); font-size: 0.8125rem; color: var(--dt-ink-2); cursor: pointer; transition: all 0.15s; }
.audit-tab.active { background: var(--dt-primary); color: #fff; font-weight: 600; }
.tab-count { font-size: 0.6875rem; background: rgba(255,255,255,0.2); padding: 0 6px; border-radius: var(--dt-radius-sm); }
.audit-tab:not(.active) .tab-count { background: var(--dt-muted); color: var(--dt-ink-3); }
.filter-right { display: flex; align-items: center; gap: 8px; }
.search-mini { display: flex; align-items: center; gap: 6px; height: 32px; padding: 0 10px; border-radius: var(--dt-radius-md); background: var(--dt-glass-light); border: 1px solid var(--dt-border); color: var(--dt-ink-3); }
.search-mini input { border: none; outline: none; background: transparent; font-size: 0.75rem; width: 120px; font-family: var(--dt-font); }
</style>
