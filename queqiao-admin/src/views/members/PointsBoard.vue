<script setup lang="ts">
/** 会员积分榜（1-A）：累计积分排行 + 积分规则说明 */
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  fetchPointsLeaderboard,
  fetchPointRules,
  type LeaderboardRow,
  type PointRule,
} from '@/api'

const rows = ref<LeaderboardRow[]>([])
const rules = ref<PointRule[]>([])
const loading = ref(false)

const load = async () => {
  loading.value = true
  try {
    const [board, ruleList] = await Promise.all([
      fetchPointsLeaderboard(20),
      fetchPointRules(),
    ])
    rows.value = board
    rules.value = ruleList
  } catch (e: any) {
    ElMessage.error(e?.message || '积分榜加载失败')
  } finally {
    loading.value = false
  }
}

const rankClass = (i: number) =>
  i === 0 ? 'rank rank-1' : i === 1 ? 'rank rank-2' : i === 2 ? 'rank rank-3' : 'rank'

onMounted(load)
</script>

<template>
  <div class="points-page">
    <div class="page-head">
      <div>
        <h2 class="page-title">会员积分榜</h2>
        <p class="page-sub">按累计积分对会员进行排序，激励资料完善、约见达成与牵线成功。</p>
      </div>
      <button class="btn btn-outline" :disabled="loading" @click="load">
        <app-icon name="refresh" :size="15" />刷新
      </button>
    </div>

    <div class="content-row">
      <!-- 排行榜 -->
      <el-card class="board-card" shadow="never">
        <template #header>
          <div class="card-head">
            <span>积分排行 TOP 20</span>
            <span class="card-hint">积分越高，红娘贡献越大</span>
          </div>
        </template>
        <el-table :data="rows" v-loading="loading" empty-text="暂无积分数据" row-key="memberId">
          <el-table-column label="排名" width="80">
            <template #default="{ $index }">
              <span :class="rankClass($index)">{{ $index + 1 }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="memberName" label="会员" min-width="120" />
          <el-table-column label="等级" width="120">
            <template #default="{ row }">
              <el-tag size="small" type="warning" effect="light">{{ row.level }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="累计积分" min-width="120" align="right">
            <template #default="{ row }">
              <span class="total">{{ row.total }}</span>
            </template>
          </el-table-column>
        </el-table>
      </el-card>

      <!-- 积分规则 -->
      <el-card class="rule-card" shadow="never">
        <template #header>
          <div class="card-head"><span>积分规则</span></div>
        </template>
        <ul class="rule-list">
          <li v-for="r in rules" :key="r.type" class="rule-item">
            <div class="rule-main">
              <span class="rule-label">{{ r.label }}</span>
              <span class="rule-points">+{{ r.points }}</span>
            </div>
            <span class="rule-tag">{{ r.once ? '一次性' : '可累计' }}</span>
          </li>
          <li v-if="!rules.length" class="rule-empty">加载中…</li>
        </ul>
        <div class="rule-foot">
          等级依据累计积分自动授予：见习红娘 → 红娘助手(100) → 资深红娘(300) → 金牌红娘(700) → 首席红娘(1500)。
        </div>
      </el-card>
    </div>
  </div>
</template>

<style scoped>
.points-page { display: flex; flex-direction: column; gap: 16px; }
.page-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.page-title { font-size: 1.25rem; font-weight: 600; color: var(--dt-ink-1); margin: 0; }
.page-sub { font-size: 0.8125rem; color: var(--dt-ink-3); margin: 6px 0 0; }
.content-row { display: flex; gap: 20px; align-items: flex-start; }
.board-card { flex: 1; min-width: 0; }
.rule-card { width: 320px; flex-shrink: 0; }
.card-head { display: flex; align-items: baseline; justify-content: space-between; font-weight: 600; color: var(--dt-ink-1); }
.card-hint { font-size: 0.75rem; font-weight: 400; color: var(--dt-ink-4); }
.total { font-size: 1.125rem; font-weight: 600; color: var(--dt-primary); }
.rank {
  display: inline-flex; align-items: center; justify-content: center;
  width: 26px; height: 26px; border-radius: var(--dt-radius-sm);
  font-weight: 600; font-size: 0.875rem; color: var(--dt-ink-3);
  background: var(--dt-muted);
}
.rank-1 { color: #fff; background: var(--dt-gold); }
.rank-2 { color: #fff; background: var(--dt-ink-3); }
.rank-3 { color: #fff; background: var(--dt-warning); }
.rule-list { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 12px; }
.rule-item {
  display: flex; align-items: center; justify-content: space-between;
  padding: 12px 14px; border-radius: var(--dt-radius-md);
  background: var(--dt-muted); border: 1px solid var(--dt-border);
}
.rule-main { display: flex; align-items: baseline; gap: 10px; }
.rule-label { font-weight: 600; color: var(--dt-ink-1); }
.rule-points { font-size: 1.125rem; font-weight: 600; color: var(--dt-success); }
.rule-tag { font-size: 0.75rem; color: var(--dt-ink-3); }
.rule-empty { color: var(--dt-ink-4); }
.rule-foot {
  margin-top: 16px; padding-top: 14px; border-top: 1px solid var(--dt-border);
  font-size: 0.75rem; line-height: 1.6; color: var(--dt-ink-3);
}
</style>
