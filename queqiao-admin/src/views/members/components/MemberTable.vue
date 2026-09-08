<script setup lang="ts">
/** 会员管理 · 会员数据表格 + 底部分页 */
import type { Member } from '@/api'

defineProps<{ data: Member[]; total: number; loading?: boolean }>()
const emit = defineEmits<{
  (e: 'row-click', row: Member): void
  (e: 'view', row: Member): void
  (e: 'edit', row: Member): void
  (e: 'match', row: Member): void
  (e: 'command', payload: { cmd: string; row: Member }): void
}>()

const page = defineModel<number>('page', { required: true })
const pageSize = defineModel<number>('pageSize', { required: true })

const onRowClick = (row: Member) => emit('row-click', row)

/**
 * 会员库使用「语义色」状态映射（已认证=绿 / 认证中=蓝），
 * 与 @/utils/member 的 statusClass（牵线视角：已认证=酒红 active）不同，
 * 为保持本页原有视觉，此处保留本页专用映射。
 */
type PillType = 'success' | 'warning' | 'error' | 'info' | 'active' | 'gold' | 'neutral'
const pillType = (s: Member['status']): PillType =>
  s === '已认证' ? 'success' : s === '待审核' ? 'warning' : s === '认证中' ? 'info' : s === '已暂停' ? 'neutral' : 'active'
</script>

<template>
  <GlassCard class="table-card" padding="">
    <el-table v-loading="loading" :data="data" class="dt-table" @row-click="onRowClick">
      <el-table-column label="会员" min-width="180">
        <template #default="{ row }">
          <div class="table-member">
            <BaseAvatar :name="row.name" :bg="row.avatarBg" :size="36" />
            <div>
              <div class="table-member-name">{{ row.name }}</div>
              <div class="table-member-id">{{ row.id }}</div>
            </div>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="性别/年龄" width="110">
        <template #default="{ row }"><span class="td-ink2">{{ row.gender }} · {{ row.age }}</span></template>
      </el-table-column>
      <el-table-column label="城市" min-width="120">
        <template #default="{ row }"><span class="td-ink2"><app-icon name="map-pin" :size="12" /> {{ row.city }}</span></template>
      </el-table-column>
      <el-table-column label="学历/职业" min-width="140">
        <template #default="{ row }">
          <div class="job-main">{{ row.job }}</div>
          <div class="job-sub">{{ row.education }}</div>
        </template>
      </el-table-column>
      <el-table-column label="等级" width="100">
        <template #default="{ row }"><LevelBadge :level="row.level" /></template>
      </el-table-column>
      <el-table-column label="状态" width="110">
        <template #default="{ row }"><StatusPill :type="pillType(row.status)" :text="row.status" /></template>
      </el-table-column>
      <el-table-column label="负责红娘" width="100">
        <template #default="{ row }"><span class="td-ink2">{{ row.agent }}</span></template>
      </el-table-column>
      <el-table-column label="牵线/成功率" width="120">
        <template #default="{ row }"><span class="td-ink2">{{ row.matchCount }}次 / {{ row.successRate }}%</span></template>
      </el-table-column>
      <el-table-column label="最近活跃" width="110">
        <template #default="{ row }"><span class="td-ink3"><app-icon name="clock" :size="12" /> {{ row.lastActive }}</span></template>
      </el-table-column>
      <el-table-column label="操作" width="200" align="right" fixed="right">
        <template #default="{ row }">
          <div class="row-actions" @click.stop>
            <button class="icon-mini" title="查看" @click="emit('view', row)"><app-icon name="eye" :size="15" /></button>
            <button class="icon-mini" title="编辑" @click="emit('edit', row)"><app-icon name="edit" :size="15" /></button>
            <button class="icon-mini gold" title="牵线" @click="emit('match', row)"><app-icon name="heart" :size="15" /></button>
            <el-dropdown trigger="click" @command="(c: string) => emit('command', { cmd: c, row })">
              <button class="icon-mini" title="更多"><app-icon name="more" :size="15" /></button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="verify">通过认证</el-dropdown-item>
                  <el-dropdown-item command="suspend">暂停会员</el-dropdown-item>
                  <el-dropdown-item command="resume">恢复正常</el-dropdown-item>
                  <el-dropdown-item command="assign" divided>分配红娘</el-dropdown-item>
                  <el-dropdown-item command="delete" divided>删除会员</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </template>
      </el-table-column>
      <template #empty><el-empty description="暂无会员数据" :image-size="80" /></template>
    </el-table>

    <div class="table-footer">
      <span class="total-text">共 {{ total.toLocaleString() }} 位会员</span>
      <el-pagination
        v-model:current-page="page" v-model:page-size="pageSize"
        :total="total" :page-sizes="[10, 20, 50]"
        layout="sizes, prev, pager, next" background small
      />
    </div>
  </GlassCard>
</template>

<style scoped>
.table-card { flex: 1; min-width: 0; padding: 8px 8px 0; }
.table-member { display: flex; align-items: center; gap: 10px; }
.table-member-name { font-weight: 600; color: var(--dt-ink-1); }
.table-member-id { font-size: 0.75rem; color: var(--dt-ink-4); }
.td-ink2 { color: var(--dt-ink-2); }
.td-ink3 { color: var(--dt-ink-3); display: inline-flex; align-items: center; gap: 3px; }
.job-main { color: var(--dt-ink-1); font-weight: 500; }
.job-sub { font-size: 0.75rem; color: var(--dt-ink-4); }
.row-actions { display: inline-flex; gap: 6px; }
.icon-mini {
  width: 30px; height: 30px; border-radius: 8px; display: inline-flex; align-items: center; justify-content: center;
  background: var(--dt-muted); color: var(--dt-ink-3); border: 1px solid var(--dt-border); cursor: pointer; transition: all 0.15s;
}
.icon-mini:hover { color: var(--dt-primary); background: var(--dt-primary-soft); }
.icon-mini.gold { color: var(--dt-gold-dark); }
.icon-mini.gold:hover { background: var(--dt-gold-soft); }

.table-footer { display: flex; align-items: center; justify-content: space-between; padding: 14px 12px; }
.total-text { font-size: 0.8125rem; color: var(--dt-ink-3); }
</style>
