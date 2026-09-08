<script setup lang="ts">
/**
 * ReportTable — 导出记录表（真实记录 + 真实下载 / 删除）
 */
import { computed, ref } from 'vue'
import type { ReportRecord } from '@/api'

const props = defineProps<{ records: ReportRecord[]; loading?: boolean }>()
const emit = defineEmits<{
  (e: 'download', row: ReportRecord): void
  (e: 'remove', row: ReportRecord): void
}>()

const page = ref(1)
const pageSize = 10
const paged = computed(() =>
  props.records.slice((page.value - 1) * pageSize, page.value * pageSize),
)

const catClass = (c: string) =>
  c === '财务' ? 'primary' : c === '红娘' ? 'gold' : c === '会员' ? 'info' : c === '活动' ? 'success' : 'warning'
</script>

<template>
  <div class="glass-card table-card">
    <div class="section-title" style="margin: 6px 6px 0">
      <span class="section-title-bar" />导出记录
    </div>
    <el-table v-loading="loading" :data="paged" class="dt-table" empty-text="暂无导出记录">
      <el-table-column label="报表名称" min-width="180">
        <template #default="{ row }"><span style="font-weight: 600">{{ row.name }}</span></template>
      </el-table-column>
      <el-table-column label="类型" width="100">
        <template #default="{ row }">
          <span class="type-badge" :class="catClass(row.category)">{{ row.category }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="format" label="格式" width="80" />
      <el-table-column prop="range" label="时间范围" min-width="170" show-overflow-tooltip />
      <el-table-column prop="exportedAt" label="导出时间" width="150" />
      <el-table-column prop="operator" label="操作人" width="100" />
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <span class="status-pill" :class="row.status === '已完成' ? 'success' : 'warning'">
            <span class="dot" />{{ row.status }}
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="size" label="文件大小" width="90" />
      <el-table-column label="操作" width="110" align="right">
        <template #default="{ row }">
          <button class="icon-mini" title="下载 CSV" @click="emit('download', row)">
            <app-icon name="download" :size="15" />
          </button>
          <button class="icon-mini danger" title="删除记录" @click="emit('remove', row)">
            <app-icon name="trash" :size="15" />
          </button>
        </template>
      </el-table-column>
    </el-table>
    <div class="table-footer">
      <span class="total-text">共 {{ records.length }} 条</span>
      <el-pagination
        v-model:current-page="page"
        :total="records.length"
        :page-size="pageSize"
        layout="prev, pager, next"
        background
        small
      />
    </div>
  </div>
</template>

<style scoped>
.table-card { padding: 8px 8px 0; }
.type-badge { font-size: 0.6875rem; font-weight: 700; padding: 3px 10px; border-radius: 999px; }
.type-badge.primary { background: var(--dt-primary-soft-2); color: var(--dt-primary); }
.type-badge.gold { background: var(--dt-gold-soft); color: var(--dt-gold-dark); }
.type-badge.info { background: var(--dt-info-soft); color: var(--dt-info); }
.type-badge.success { background: var(--dt-success-soft); color: var(--dt-success); }
.type-badge.warning { background: var(--dt-warning-soft); color: var(--dt-warning); }
.icon-mini { width: 30px; height: 30px; border-radius: 8px; display: inline-flex; align-items: center; justify-content: center; background: var(--dt-muted); color: var(--dt-ink-3); border: 1px solid var(--dt-border); cursor: pointer; margin-left: 6px; }
.icon-mini:hover { color: var(--dt-primary); }
.icon-mini.danger:hover { color: var(--dt-error); border-color: var(--dt-error); }
.table-footer { display: flex; align-items: center; justify-content: space-between; padding: 14px 12px; }
.total-text { font-size: 0.8125rem; color: var(--dt-ink-3); }
</style>
