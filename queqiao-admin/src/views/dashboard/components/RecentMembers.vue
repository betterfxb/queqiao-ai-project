<script setup lang="ts">
/** Dashboard · 最新注册会员表格 */
import type { Member } from '@/api'
import { rowAction, statusClass } from '@/utils/member'

defineProps<{ members: Member[] }>()
const emit = defineEmits<{
  /** 点击「查看全部」 */
  (e: 'more'): void
  /** 点击「查看资料」 */
  (e: 'view', row: Member): void
}>()

/** statusClass 返回值收窄为 StatusPill 的 type 联合 */
type PillType = 'success' | 'warning' | 'error' | 'info' | 'active' | 'gold' | 'neutral'
const pillType = (s: Member['status']) => statusClass(s) as PillType
</script>

<template>
  <GlassCard class="table-section" padding="">
    <SectionHeader title="最新注册会员" more-text="查看全部" @more="emit('more')" />

    <el-table :data="members" class="dt-table" :show-header="true">
      <el-table-column label="会员" min-width="200">
        <template #default="{ row }">
          <div class="table-member">
            <BaseAvatar :name="row.name" :bg="row.avatarBg" :size="38" />
            <div>
              <div class="table-member-name">{{ row.name }}</div>
              <div class="table-member-id">ID: {{ row.id }}</div>
            </div>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="年龄" width="70">
        <template #default="{ row }"><span class="td-ink2">{{ row.age }}</span></template>
      </el-table-column>
      <el-table-column label="会员等级" width="110">
        <template #default="{ row }"><LevelBadge :level="row.level" /></template>
      </el-table-column>
      <el-table-column label="负责红娘" width="100">
        <template #default="{ row }"><span class="td-ink2">{{ row.agent }}</span></template>
      </el-table-column>
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <StatusPill :type="pillType(row.status)" :text="row.status" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="180" align="right">
        <template #default="{ row }">
          <div class="row-actions">
            <button class="btn btn-sm btn-ghost" @click="emit('view', row)">查看资料</button>
            <button class="btn btn-sm btn-outline">{{ rowAction(row.status) }}</button>
          </div>
        </template>
      </el-table-column>
    </el-table>
  </GlassCard>
</template>

<style scoped>
.table-section { padding: 16px; margin-top: 16px; }
.table-member { display: flex; align-items: center; gap: 12px; }
.table-member-name { font-weight: 600; color: var(--dt-ink-1); }
.table-member-id { font-size: 0.75rem; color: var(--dt-ink-4); }
.td-ink2 { color: var(--dt-ink-2); }
.row-actions { display: inline-flex; gap: 8px; }
</style>
