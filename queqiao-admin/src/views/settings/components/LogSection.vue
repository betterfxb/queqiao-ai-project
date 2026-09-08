<script setup lang="ts">
/**
 * 系统设置 · 操作日志（#sec-log）
 * 数据来自后端 GET /api/logs（真实操作留痕）
 */
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchLogs, type OperationLog } from '@/api'

const rows = ref<OperationLog[]>([])
const loading = ref(false)
const showAll = ref(false)

const load = async () => {
  loading.value = true
  try {
    rows.value = await fetchLogs()
  } catch (e: any) {
    ElMessage.error('日志加载失败：' + (e?.message ?? '未知错误'))
  } finally {
    loading.value = false
  }
}
onMounted(load)

const shown = computed(() => (showAll.value ? rows.value : rows.value.slice(0, 8)))
const fmt = (t?: string) => (t ? String(t).slice(0, 16).replace('T', ' ') : '—')
</script>

<template>
  <section id="sec-log" class="glass-card setting-card">
    <section-header title="操作日志">
      <template #actions>
        <button class="link-btn" :disabled="loading" @click="load">
          <app-icon name="rotate-ccw" :size="14" /> 刷新
        </button>
      </template>
    </section-header>

    <el-table
      v-loading="loading"
      :data="shown"
      style="width: 100%"
      header-row-class-name="sec-table-head"
      empty-text="暂无操作记录"
    >
      <el-table-column label="时间" width="160">
        <template #default="{ row }">{{ fmt(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column prop="action" label="操作" min-width="130" />
      <el-table-column prop="target" label="对象" width="140" />
      <el-table-column prop="detail" label="详情" min-width="180" show-overflow-tooltip />
      <el-table-column prop="userName" label="操作人" width="110" />
      <el-table-column prop="ip" label="IP 地址" width="150" show-overflow-tooltip />
    </el-table>

    <div class="table-footer">
      <span class="count">共 {{ rows.length }} 条</span>
      <button v-if="rows.length > 8" class="link-btn" @click="showAll = !showAll">
        {{ showAll ? '收起 ↑' : '查看全部日志 →' }}
      </button>
    </div>
  </section>
</template>

<style scoped>
.setting-card { padding: 22px 24px; }
.setting-card :deep(.section-header) { margin-bottom: 18px; }

.table-footer {
  margin-top: 14px; display: flex; align-items: center;
  justify-content: space-between; gap: 12px;
}
.count { font-size: 0.7813rem; color: var(--dt-ink-3); }
.link-btn {
  background: transparent; border: none; cursor: pointer;
  color: var(--dt-primary); font-size: 0.8125rem; font-weight: 600;
  font-family: var(--dt-font);
  display: inline-flex; align-items: center; gap: 5px;
}
.link-btn:hover { color: var(--dt-primary-light); }
.link-btn:disabled { opacity: .5; cursor: not-allowed; }
</style>
